package com.rtms.backend.training.service;

import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.CompleteWorkoutRequest;
import com.rtms.backend.training.dto.GroomConflictRow;
import com.rtms.backend.training.dto.PlanWorkoutItemResponse;
import com.rtms.backend.training.entity.HorseTrainingPlan;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.entity.TrainingWorkout;
import com.rtms.backend.training.enums.LotStatus;
import com.rtms.backend.training.enums.TrainingDay;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import com.rtms.backend.training.enums.WorkoutStatus;
import com.rtms.backend.training.repository.HorseTrainingPlanRepository;
import com.rtms.backend.training.repository.SubjectRepository;
import com.rtms.backend.training.repository.TrainingLotRepository;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingWorkoutServiceTest {

    @Mock
    private TrainingWorkoutRepository workoutRepository;

    @Mock
    private HorseTrainingPlanRepository planRepository;

    @Mock
    private TrainingLotRepository lotRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TrainingLotService lotService;

    private TrainingWorkoutService workoutService;

    private final AuthenticatedUser trainerUser = new AuthenticatedUser(9L, "trainer@example.com", "HEAD_TRAINER");

    @BeforeEach
    void setUp() {
        workoutService = new TrainingWorkoutService(
                workoutRepository, planRepository, lotRepository, subjectRepository, lotService);
    }

    /** Dựng một buổi tập tương lai. (Builds a future workout.) */
    private TrainingWorkout futureWorkout(Long id, Long lotId, Long horseId, Long groomId) {
        TrainingWorkout w = new TrainingWorkout();
        w.setId(id);
        w.setLotId(lotId);
        w.setHorseId(horseId);
        w.setAssignedToId(groomId);
        w.setStatus(WorkoutStatus.SCHEDULED);
        return w;
    }

    // =================================================================
    // buildWorkoutItems
    // =================================================================

    @Test
    @DisplayName("Plan workouts come straight from the joined query")
    void testBuildWorkoutItems_DelegatesToJoinedQuery() {
        List<PlanWorkoutItemResponse> items = List.of();
        when(workoutRepository.findPlanWorkoutItems(27L)).thenReturn(items);

        assertSame(items, workoutService.buildWorkoutItems(27L));
    }

    // =================================================================
    // createWorkouts
    // =================================================================

    private Horse horse(Long id) {
        Horse horse = new Horse();
        horse.setId(id);
        horse.setName("Horse " + id);
        return horse;
    }

    private Subject subject(Long id, String name) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        return subject;
    }

    private HorseTrainingPlan plan(Long id, Long horseId) {
        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(id);
        plan.setHorseId(horseId);
        return plan;
    }

    private void stubWorkoutSaving() {
        when(workoutRepository.saveAndFlush(any(TrainingWorkout.class))).thenAnswer(inv -> {
            TrainingWorkout w = inv.getArgument(0);
            w.setId(w.getHorseId() * 100 + w.getLotId());
            return w;
        });
    }

    @Test
    @DisplayName("createWorkouts: one workout per horse per session, saved and flushed one by one")
    void testCreateWorkouts_OnePerHorsePerSession() {
        Subject warmUp = subject(50L, "Warm-up");
        TrainingLot lot = new TrainingLot();
        lot.setId(500L);
        lot.setLotDate(LocalDate.of(2026, 10, 5));
        lot.setStartTime(LocalTime.of(6, 0));
        lot.setEndTime(LocalTime.of(6, 30));
        when(lotService.findOrCreateLot(eq(9L), any(LocalDate.class), eq(warmUp), anyLong())).thenReturn(lot);
        stubWorkoutSaving();

        List<EnrolledHorse> enrolled = List.of(new EnrolledHorse(horse(1L), 4L), new EnrolledHorse(horse(2L), 5L));
        Map<Long, HorseTrainingPlan> plans = new LinkedHashMap<>();
        plans.put(1L, plan(1001L, 1L));
        plans.put(2L, plan(1002L, 2L));

        Map<Long, List<PlanWorkoutItemResponse>> result = workoutService.createWorkouts(
                new TrainingWorkoutService.PlanSchedule(9L, LocalDate.of(2026, 10, 5),
                        Set.of(TrainingDay.MONDAY, TrainingDay.WEDNESDAY), 2, List.of(warmUp)),
                enrolled, plans);

        assertEquals(2, result.get(1L).size(), "2 sessions for horse 1");
        assertEquals(2, result.get(2L).size(), "2 sessions for horse 2");
        verify(workoutRepository, times(4)).saveAndFlush(any(TrainingWorkout.class)); // 2 horses * 2 sessions
        // Mỗi con ngựa dùng Groom riêng của nó để xếp lot (each horse is placed with its own groom)
        verify(lotService, times(2)).findOrCreateLot(eq(9L), any(LocalDate.class), eq(warmUp), eq(4L));
        verify(lotService, times(2)).findOrCreateLot(eq(9L), any(LocalDate.class), eq(warmUp), eq(5L));
    }

    @Test
    @DisplayName("createWorkouts: subjects rotate in order across sessions and workouts are SCHEDULED for the right plan")
    void testCreateWorkouts_RotatesSubjects() {
        Subject first = subject(1L, "Steady run");
        Subject second = subject(2L, "Sprint");
        TrainingLot lot = new TrainingLot();
        lot.setId(500L);
        when(lotService.findOrCreateLot(anyLong(), any(LocalDate.class), any(Subject.class), anyLong())).thenReturn(lot);
        stubWorkoutSaving();

        Map<Long, HorseTrainingPlan> plans = new LinkedHashMap<>();
        plans.put(1L, plan(1001L, 1L));

        workoutService.createWorkouts(
                new TrainingWorkoutService.PlanSchedule(9L, LocalDate.of(2026, 10, 5),
                        Set.of(TrainingDay.MONDAY, TrainingDay.TUESDAY, TrainingDay.WEDNESDAY), 3,
                        List.of(first, second)),
                List.of(new EnrolledHorse(horse(1L), 4L)), plans);

        ArgumentCaptor<Subject> subjects = ArgumentCaptor.forClass(Subject.class);
        verify(lotService, times(3)).findOrCreateLot(eq(9L), any(LocalDate.class), subjects.capture(), eq(4L));
        assertEquals(List.of(first, second, first), subjects.getAllValues(), "Session i uses subject i % n");

        ArgumentCaptor<TrainingWorkout> saved = ArgumentCaptor.forClass(TrainingWorkout.class);
        verify(workoutRepository, times(3)).saveAndFlush(saved.capture());
        saved.getAllValues().forEach(w -> {
            assertEquals(1001L, w.getPlanId());
            assertEquals(WorkoutStatus.SCHEDULED, w.getStatus());
            assertEquals(4L, w.getAssignedToId());
        });
    }

    // =================================================================
    // completeWorkout
    // =================================================================

    @Test
    @DisplayName("9. Trainer closes a workout: metrics, performance rating and feedback are recorded")
    void testCompleteWorkout_Success() {
        Long workoutId = 300L;
        CompleteWorkoutRequest req = new CompleteWorkoutRequest();
        req.setPerformanceRating(9);
        req.setActualDistanceMeters(BigDecimal.valueOf(1200));
        req.setActualDurationMinutes(BigDecimal.valueOf(45));
        req.setTopSpeedKmh(BigDecimal.valueOf(58.5));
        req.setAverageHeartRate(140);
        req.setTrainerFeedback("The horse ran very steadily and kept its energy well");

        TrainingWorkout workout = new TrainingWorkout();
        workout.setId(workoutId);
        workout.setPlanId(50L);
        workout.setLotId(600L);
        workout.setStatus(WorkoutStatus.SCHEDULED);

        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(50L);
        plan.setTrainerId(9L); // same trainer
        plan.setStatus(TrainingPlanStatus.ACTIVE);

        when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));
        when(planRepository.findById(50L)).thenReturn(Optional.of(plan));
        when(workoutRepository.saveAndFlush(any(TrainingWorkout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workoutRepository.countByPlanIdAndStatusNotIn(eq(50L), anyList())).thenReturn(2L); // 2 left

        TrainingLot lot = new TrainingLot();
        lot.setId(600L);
        lot.setSubjectId(77L);
        when(lotRepository.findById(600L)).thenReturn(Optional.of(lot));

        Subject subject = new Subject();
        subject.setId(77L);
        subject.setName("Acceleration drill");
        when(subjectRepository.findById(77L)).thenReturn(Optional.of(subject));

        PlanWorkoutItemResponse response = workoutService.completeWorkout(workoutId, req, trainerUser);

        assertNotNull(response);
        assertEquals("Acceleration drill", response.getSubjectName());
        assertEquals(WorkoutStatus.COMPLETED, workout.getStatus());
        assertEquals(9, workout.getPerformanceRating());
        assertEquals("The horse ran very steadily and kept its energy well", workout.getTrainerFeedback());
        assertEquals(BigDecimal.valueOf(58.5), workout.getTopSpeedKmh());
    }

    @Test
    @DisplayName("10. Another trainer may not close a workout of the trainer in charge")
    void testCompleteWorkout_WrongTrainer_ThrowsAccessDenied() {
        Long workoutId = 300L;
        CompleteWorkoutRequest req = new CompleteWorkoutRequest();
        req.setPerformanceRating(8);

        TrainingWorkout workout = new TrainingWorkout();
        workout.setId(workoutId);
        workout.setPlanId(50L);

        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(50L);
        plan.setTrainerId(888L); // differs from trainerUser (9L)

        when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));
        when(planRepository.findById(50L)).thenReturn(Optional.of(plan));

        assertThrows(AccessDeniedException.class,
                () -> workoutService.completeWorkout(workoutId, req, trainerUser));
    }

    @Test
    @DisplayName("11. A cancelled workout cannot be closed")
    void testCompleteWorkout_CancelledWorkout_ThrowsIllegalStateException() {
        Long workoutId = 300L;

        TrainingWorkout workout = new TrainingWorkout();
        workout.setId(workoutId);
        workout.setPlanId(50L);
        workout.setStatus(WorkoutStatus.CANCELLED);

        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(50L);
        plan.setTrainerId(9L);

        when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));
        when(planRepository.findById(50L)).thenReturn(Optional.of(plan));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> workoutService.completeWorkout(workoutId, new CompleteWorkoutRequest(), trainerUser));
        assertTrue(ex.getMessage().contains("was cancelled"));
    }

    @Test
    @DisplayName("12. A performance rating outside 1..10 is rejected")
    void testCompleteWorkout_InvalidRating_ThrowsIllegalArgument() {
        Long workoutId = 300L;
        CompleteWorkoutRequest req = new CompleteWorkoutRequest();
        req.setPerformanceRating(15); // > 10

        TrainingWorkout workout = new TrainingWorkout();
        workout.setId(workoutId);
        workout.setPlanId(50L);
        workout.setStatus(WorkoutStatus.SCHEDULED);

        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(50L);
        plan.setTrainerId(9L);

        when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));
        when(planRepository.findById(50L)).thenReturn(Optional.of(plan));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> workoutService.completeWorkout(workoutId, req, trainerUser));
        assertTrue(ex.getMessage().contains("between 1 and 10"));
    }

    @Test
    @DisplayName("13. The last workout completed closes the plan as COMPLETED")
    void testCompleteWorkout_LastWorkout_AutoCompletesPlan() {
        HorseTrainingPlan plan = setUpWorkoutScenario(TrainingPlanStatus.ACTIVE, 0L);

        workoutService.completeWorkout(101L, new CompleteWorkoutRequest(), trainerUser);

        assertEquals(TrainingPlanStatus.COMPLETED, plan.getStatus(),
                "The plan must become COMPLETED when its last workout is done");
        verify(planRepository).save(plan);
    }

    // =================================================================
    // Lot status when a workout is completed
    // =================================================================

    @Test
    @DisplayName("All workouts in the lot done -> the lot becomes COMPLETED")
    void testCompleteWorkout_AllWorkoutsInLotCompleted_AutoCompletesLot() {
        Long workoutId = 301L;
        CompleteWorkoutRequest req = new CompleteWorkoutRequest();
        req.setPerformanceRating(9);

        TrainingWorkout workout = new TrainingWorkout();
        workout.setId(workoutId);
        workout.setPlanId(50L);
        workout.setLotId(601L);
        workout.setStatus(WorkoutStatus.SCHEDULED);

        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(50L);
        plan.setTrainerId(9L);
        plan.setStatus(TrainingPlanStatus.ACTIVE);

        TrainingLot lot = new TrainingLot();
        lot.setId(601L);
        lot.setSubjectId(77L);
        lot.setStatus(LotStatus.SCHEDULED);

        when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));
        when(planRepository.findById(50L)).thenReturn(Optional.of(plan));
        when(workoutRepository.saveAndFlush(any(TrainingWorkout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workoutRepository.countByPlanIdAndStatusNotIn(eq(50L), anyList())).thenReturn(2L);
        when(lotRepository.findById(601L)).thenReturn(Optional.of(lot));
        when(workoutRepository.countByLotIdAndStatusNotIn(eq(601L), anyList())).thenReturn(0L);

        workoutService.completeWorkout(workoutId, req, trainerUser);

        assertEquals(LotStatus.COMPLETED, lot.getStatus(),
                "The lot must become COMPLETED when every workout in it is completed or cancelled");
        verify(lotRepository).save(lot);
    }

    @Test
    @DisplayName("Other workouts still open in the lot -> the lot stays SCHEDULED")
    void testCompleteWorkout_OtherWorkoutsInLotStillRemaining_LotRemainsScheduled() {
        Long workoutId = 302L;
        CompleteWorkoutRequest req = new CompleteWorkoutRequest();
        req.setPerformanceRating(7);

        TrainingWorkout workout = new TrainingWorkout();
        workout.setId(workoutId);
        workout.setPlanId(50L);
        workout.setLotId(602L);
        workout.setStatus(WorkoutStatus.SCHEDULED);

        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(50L);
        plan.setTrainerId(9L);
        plan.setStatus(TrainingPlanStatus.ACTIVE);

        TrainingLot lot = new TrainingLot();
        lot.setId(602L);
        lot.setSubjectId(77L);
        lot.setStatus(LotStatus.SCHEDULED);

        when(workoutRepository.findById(workoutId)).thenReturn(Optional.of(workout));
        when(planRepository.findById(50L)).thenReturn(Optional.of(plan));
        when(workoutRepository.saveAndFlush(any(TrainingWorkout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workoutRepository.countByPlanIdAndStatusNotIn(eq(50L), anyList())).thenReturn(2L);
        when(lotRepository.findById(602L)).thenReturn(Optional.of(lot));
        when(workoutRepository.countByLotIdAndStatusNotIn(eq(602L), anyList())).thenReturn(1L); // 1 still open

        workoutService.completeWorkout(workoutId, req, trainerUser);

        assertEquals(LotStatus.SCHEDULED, lot.getStatus(),
                "The lot must stay SCHEDULED because another horse's workout is not finished");
        verify(lotRepository, never()).save(lot);
    }

    // =================================================================
    // Plan status lifecycle
    // =================================================================

    /**
     * Dựng bối cảnh đóng một buổi tập. (Builds the context for closing a workout.)
     *
     * @param remainingAfter số buổi CÒN LẠI sau khi đóng buổi này, 0 = đây là buổi cuối của khóa
     *                       (workouts REMAINING after this one, 0 = this is the last workout of the course)
     */
    private HorseTrainingPlan setUpWorkoutScenario(TrainingPlanStatus planStatus, long remainingAfter) {
        HorseTrainingPlan plan = new HorseTrainingPlan();
        plan.setId(27L);
        plan.setTrainerId(9L);
        plan.setStatus(planStatus);

        TrainingWorkout workout = futureWorkout(101L, 71L, 15L, 11L);
        workout.setPlanId(27L);

        TrainingLot lot = new TrainingLot();
        lot.setId(71L);
        lot.setSubjectId(1L);

        Subject subject = new Subject();
        subject.setId(1L);
        subject.setName("Steady run");

        when(workoutRepository.findById(101L)).thenReturn(Optional.of(workout));
        when(planRepository.findById(27L)).thenReturn(Optional.of(plan));
        when(workoutRepository.saveAndFlush(any(TrainingWorkout.class))).thenAnswer(i -> i.getArgument(0));
        when(workoutRepository.countByPlanIdAndStatusNotIn(eq(27L), anyList())).thenReturn(remainingAfter);
        when(lotRepository.findById(71L)).thenReturn(Optional.of(lot));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));

        return plan;
    }

    @Test
    @DisplayName("An UPCOMING plan can still be closed when its last workout is completed")
    void testCompleteWorkout_UpcomingPlan_StillCompletes() {
        HorseTrainingPlan plan = setUpWorkoutScenario(TrainingPlanStatus.UPCOMING, 0L);

        workoutService.completeWorkout(101L, new CompleteWorkoutRequest(), trainerUser);

        // Trước đây điều kiện "status == ACTIVE" khiến mọi kế hoạch tạo cho ngày tương lai KHÔNG BAO GIỜ đóng được.
        // (Before, the "status == ACTIVE" condition meant a plan created for a future date could NEVER close.)
        assertEquals(TrainingPlanStatus.COMPLETED, plan.getStatus());
        verify(planRepository).save(plan);
    }

    @Test
    @DisplayName("An ACTIVE plan whose last workout is completed -> COMPLETED")
    void testCompleteWorkout_ActivePlan_Completes() {
        HorseTrainingPlan plan = setUpWorkoutScenario(TrainingPlanStatus.ACTIVE, 0L);

        workoutService.completeWorkout(101L, new CompleteWorkoutRequest(), trainerUser);

        assertEquals(TrainingPlanStatus.COMPLETED, plan.getStatus());
    }

    @Test
    @DisplayName("Workouts still open: an UPCOMING plan becomes ACTIVE, not closed")
    void testCompleteWorkout_StillRemaining_BecomesActiveNotCompleted() {
        HorseTrainingPlan plan = setUpWorkoutScenario(TrainingPlanStatus.UPCOMING, 5L);

        workoutService.completeWorkout(101L, new CompleteWorkoutRequest(), trainerUser);

        assertEquals(TrainingPlanStatus.ACTIVE, plan.getStatus(),
                "Closing one workout means the course has started");
    }

    // =================================================================
    // cancelFutureTrainingForHorse
    // =================================================================

    @Test
    @DisplayName("Blocked horse: future workouts are cancelled, emptied lots are released, running plans are cancelled")
    void testCancelFutureTraining() {
        TrainingWorkout w1 = futureWorkout(101L, 37L, 77L, 4L);
        TrainingWorkout w2 = futureWorkout(102L, 38L, 77L, 4L);
        TrainingWorkout w3 = futureWorkout(103L, 38L, 77L, 4L);   // same lot as w2

        HorseTrainingPlan running = new HorseTrainingPlan();
        running.setId(1L);
        running.setStatus(TrainingPlanStatus.ACTIVE);
        running.setNotes("Original note");

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of(w1, w2, w3));
        when(planRepository.findByHorseIdAndStatusInOrderByEndDateDesc(eq(77L), anyList()))
                .thenReturn(List.of(running));

        int cancelled = workoutService.cancelFutureTrainingForHorse(77L, "Tendon strain");

        assertEquals(3, cancelled);
        assertEquals(WorkoutStatus.CANCELLED, w1.getStatus());
        assertEquals(WorkoutStatus.CANCELLED, w2.getStatus());
        assertEquals(WorkoutStatus.CANCELLED, w3.getStatus());
        // Mỗi lot bị ảnh hưởng chỉ được kiểm tra MỘT lần (each touched lot is checked once)
        verify(lotService, times(1)).cancelLotIfEmpty(37L);
        verify(lotService, times(1)).cancelLotIfEmpty(38L);
        assertEquals(TrainingPlanStatus.CANCELLED, running.getStatus());
        assertEquals("Original note | [Auto-cancelled] Tendon strain", running.getNotes());
    }

    @Test
    @DisplayName("Blocked horse without a reason still gets a note on the cancelled plan")
    void testCancelFutureTraining_NoReason() {
        HorseTrainingPlan running = new HorseTrainingPlan();
        running.setId(1L);
        running.setStatus(TrainingPlanStatus.UPCOMING);

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class))).thenReturn(List.of());
        when(planRepository.findByHorseIdAndStatusInOrderByEndDateDesc(eq(77L), anyList()))
                .thenReturn(List.of(running));

        assertEquals(0, workoutService.cancelFutureTrainingForHorse(77L, null));
        assertEquals("[Auto-cancelled] Horse is blocked from training", running.getNotes());
        verifyNoInteractions(lotService);
    }

    // =================================================================
    // reassignFutureWorkoutsToGroom — sync the groom when the stall changes
    // =================================================================

    @Test
    @DisplayName("A horse with no plan: returns 0 and writes nothing")
    void testReassign_NoFutureWorkouts_ReturnsZero() {
        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of());

        int moved = workoutService.reassignFutureWorkoutsToGroom(77L, 5L);

        assertEquals(0, moved);
        verify(workoutRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("No conflict: every future workout moves to the new groom")
    void testReassign_NoConflict_UpdatesAll() {
        TrainingWorkout w1 = futureWorkout(101L, 37L, 77L, 4L);
        TrainingWorkout w2 = futureWorkout(102L, 38L, 77L, 4L);

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of(w1, w2));
        when(workoutRepository.findGroomConflicts(eq(77L), eq(5L), any(LocalDate.class))).thenReturn(List.of());

        int moved = workoutService.reassignFutureWorkoutsToGroom(77L, 5L);

        assertEquals(2, moved);
        assertEquals(5L, w1.getAssignedToId());
        assertEquals(5L, w2.getAssignedToId());
        verify(workoutRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("groomId = null: the check is SKIPPED but null is STILL written -> 'unassigned'")
    void testReassign_NullGroom_StillWritesNull() {
        TrainingWorkout w1 = futureWorkout(101L, 37L, 77L, 4L);

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of(w1));

        int moved = workoutService.reassignFutureWorkoutsToGroom(77L, null);

        assertEquals(1, moved);
        assertNull(w1.getAssignedToId(), "The if(newGroomId != null) only wraps the CHECK; the write always runs");
        verify(workoutRepository).saveAll(anyList());
        // Nobody in charge cannot clash with anyone, so the check must not run
        verify(workoutRepository, never()).findGroomConflicts(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("BR-09: the new groom already has another horse in the same lot -> error, nothing written")
    void testReassign_Br09Conflict_Throws() {
        TrainingWorkout moving = futureWorkout(101L, 37L, 77L, 4L);

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of(moving));
        when(workoutRepository.findGroomConflicts(eq(77L), eq(5L), any(LocalDate.class)))
                .thenReturn(List.of(new GroomConflictRow(37L, LocalDate.of(2026, 11, 2), LocalTime.of(6, 0), "Red Fox")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> workoutService.reassignFutureWorkoutsToGroom(77L, 5L));

        // The message must let the user resolve it: date, time, which lot, which horse
        assertTrue(ex.getMessage().contains("BR-09"));
        assertTrue(ex.getMessage().contains("2026-11-02"));
        assertTrue(ex.getMessage().contains("06:00"));
        assertTrue(ex.getMessage().contains("lot #37"));
        assertTrue(ex.getMessage().contains("Red Fox"));

        assertEquals(4L, moving.getAssignedToId(), "A conflict must not overwrite the groom");
        verify(workoutRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("BR-09: ALL clashing lots are listed in one message, not just the first")
    void testReassign_MultipleConflicts_ListsAll() {
        TrainingWorkout w1 = futureWorkout(101L, 37L, 77L, 4L);
        TrainingWorkout w2 = futureWorkout(102L, 38L, 77L, 4L);

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of(w1, w2));
        when(workoutRepository.findGroomConflicts(eq(77L), eq(5L), any(LocalDate.class))).thenReturn(List.of(
                new GroomConflictRow(37L, LocalDate.of(2026, 11, 2), LocalTime.of(6, 0), "Red Fox"),
                new GroomConflictRow(38L, LocalDate.of(2026, 11, 4), LocalTime.of(6, 0), "Red Fox")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> workoutService.reassignFutureWorkoutsToGroom(77L, 5L));

        assertTrue(ex.getMessage().contains("2026-11-02"));
        assertTrue(ex.getMessage().contains("2026-11-04"),
                "All lots must be listed so the user fixes them once, not one by one");
    }

    @Test
    @DisplayName("Moving to a stall of the SAME groom: no false conflict with the horse itself")
    void testReassign_SameGroom_NoSelfConflict() {
        TrainingWorkout w1 = futureWorkout(101L, 37L, 77L, 4L);

        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(List.of(w1));
        // The query excludes the moving horse itself, so it finds no conflict
        when(workoutRepository.findGroomConflicts(eq(77L), eq(4L), any(LocalDate.class))).thenReturn(List.of());

        int moved = workoutService.reassignFutureWorkoutsToGroom(77L, 4L);

        assertEquals(1, moved);
        assertEquals(4L, w1.getAssignedToId());
    }

    @Test
    @DisplayName("Completed workouts are never part of a reassignment (history keeps the old groom)")
    void testReassign_OnlyFutureScheduledAreQueried() {
        when(workoutRepository.findFutureScheduledByHorse(eq(77L), any(LocalDate.class)))
                .thenReturn(new ArrayList<>());

        workoutService.reassignFutureWorkoutsToGroom(77L, 5L);

        verify(workoutRepository).findFutureScheduledByHorse(eq(77L), eq(LocalDate.now()));
    }
}
