package com.rtms.backend.training.service;

import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.LotHorseNameRow;
import com.rtms.backend.training.dto.TrainingLotResponse;
import com.rtms.backend.training.dto.TrainingLotRow;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.entity.TrainingWorkout;
import com.rtms.backend.training.enums.LotStatus;
import com.rtms.backend.training.enums.WorkoutStatus;
import com.rtms.backend.training.repository.SubjectRepository;
import com.rtms.backend.training.repository.TrainingLotRepository;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Xem lịch lot theo vai trò, kiểm quyền xem một lot, dời và hủy lot.
 * (Role-based lot schedule views, access control when opening a lot, rescheduling and cancelling a lot.)
 */
@ExtendWith(MockitoExtension.class)
class TrainingLotViewServiceTest {

    @Mock
    private TrainingLotRepository lotRepository;

    @Mock
    private TrainingWorkoutRepository workoutRepository;

    @Mock
    private SubjectRepository subjectRepository;

    private TrainingLotService lotService;

    private final LocalDate from = LocalDate.of(2026, 11, 2);
    private final LocalDate to = LocalDate.of(2026, 11, 8);

    private final AuthenticatedUser trainer = new AuthenticatedUser(9L, "trainer@example.com", "HEAD_TRAINER");
    private final AuthenticatedUser groom = new AuthenticatedUser(4L, "groom@example.com", "GROOM");
    private final AuthenticatedUser owner = new AuthenticatedUser(5L, "owner@example.com", "HORSE_OWNER");
    private final AuthenticatedUser manager = new AuthenticatedUser(1L, "manager@example.com", "CLUB_MANAGER");

    @BeforeEach
    void setUp() {
        lotService = new TrainingLotService(lotRepository, workoutRepository, subjectRepository);
    }

    private TrainingLotRow row(Long lotId, Long trainerId, long occupied) {
        return new TrainingLotRow(lotId, trainerId, from, LocalTime.of(6, 0), LocalTime.of(7, 0),
                1L, "Steady run", 60, LotStatus.SCHEDULED, 6, occupied);
    }

    // =================================================================
    // getLots
    // =================================================================

    @Test
    @DisplayName("A reversed date range is rejected before any query")
    void testGetLots_ReversedRange() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lotService.getLots(trainer, null, to, from, false));

        assertTrue(ex.getMessage().contains("Invalid date range"));
        verifyNoInteractions(lotRepository);
    }

    @Test
    @DisplayName("A head trainer always sees their own lots, even if the client sends another trainerId")
    void testGetLots_TrainerUsesOwnId() {
        when(lotRepository.findTrainerLotRows(9L, from, to, true)).thenReturn(List.of(row(10L, 9L, 2)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of(
                new LotHorseNameRow(10L, "Red Fox"), new LotHorseNameRow(10L, "Silver Moon")));

        List<TrainingLotResponse> result = lotService.getLots(trainer, 777L, from, to, true);

        assertEquals(1, result.size());
        assertEquals(List.of("Red Fox", "Silver Moon"), result.get(0).getHorseNames());
        assertEquals(2, result.get(0).getOccupied());
        assertEquals(4, result.get(0).getRemainingSlots());
        verify(lotRepository, never()).findTrainerLotRows(eq(777L), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("A groom sees the lots where they lead a horse")
    void testGetLots_Groom() {
        when(lotRepository.findGroomLotRows(4L, from, to)).thenReturn(List.of(row(10L, 9L, 1)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of(new LotHorseNameRow(10L, "Red Fox")));

        List<TrainingLotResponse> result = lotService.getLots(groom, null, from, to, false);

        assertEquals(1, result.size());
        verify(lotRepository, never()).findOwnerLotRows(any(), any(), any());
    }

    @Test
    @DisplayName("An owner sees only lots with their horses, and only their own horse names")
    void testGetLots_OwnerSeesOnlyOwnHorses() {
        when(lotRepository.findOwnerLotRows(5L, from, to)).thenReturn(List.of(row(10L, 9L, 3)));
        when(lotRepository.findActiveHorseNamesOfOwner(anyCollection(), eq(5L)))
                .thenReturn(List.of(new LotHorseNameRow(10L, "My Horse")));

        List<TrainingLotResponse> result = lotService.getLots(owner, null, from, to, true);

        assertEquals(List.of("My Horse"), result.get(0).getHorseNames(),
                "Names of other owners horses in the same lot must not be shown");
        assertEquals(3, result.get(0).getOccupied(), "Only the total count is shared");
        verify(lotRepository, never()).findActiveHorseNames(any());
        verify(lotRepository, never()).findTrainerLotRows(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("A manager must say whose lot schedule they want")
    void testGetLots_ManagerNeedsTrainerId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> lotService.getLots(manager, null, from, to, false));

        assertTrue(ex.getMessage().contains("trainerId"));
    }

    @Test
    @DisplayName("A manager with a trainerId sees that trainer's lots, cancelled ones only if asked")
    void testGetLots_ManagerWithTrainerId() {
        when(lotRepository.findTrainerLotRows(9L, from, to, true)).thenReturn(List.of(row(10L, 9L, 0)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of());

        List<TrainingLotResponse> result = lotService.getLots(manager, 9L, from, to, true);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getHorseNames().isEmpty());
    }

    @Test
    @DisplayName("No lots: the horse-name query is not run at all")
    void testGetLots_Empty_NoNameQuery() {
        when(lotRepository.findGroomLotRows(4L, from, to)).thenReturn(List.of());

        assertTrue(lotService.getLots(groom, null, from, to, false).isEmpty());
        verify(lotRepository, never()).findActiveHorseNames(any());
    }

    @Test
    @DisplayName("Names for all lots come from ONE query, not one per lot")
    void testGetLots_OneNameQueryForManyLots() {
        when(lotRepository.findTrainerLotRows(9L, from, to, false))
                .thenReturn(List.of(row(10L, 9L, 1), row(11L, 9L, 1), row(12L, 9L, 0)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of(
                new LotHorseNameRow(10L, "A"), new LotHorseNameRow(11L, "B")));

        List<TrainingLotResponse> result = lotService.getLots(trainer, null, from, to, false);

        assertEquals(List.of("A"), result.get(0).getHorseNames());
        assertEquals(List.of("B"), result.get(1).getHorseNames());
        assertTrue(result.get(2).getHorseNames().isEmpty());
        verify(lotRepository, times(1)).findActiveHorseNames(anyCollection());
    }

    // =================================================================
    // getLot — access control
    // =================================================================

    @Test
    @DisplayName("A trainer opens their own lot")
    void testGetLot_TrainerOwnLot() {
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 1)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of(new LotHorseNameRow(10L, "Red Fox")));

        assertEquals(10L, lotService.getLot(10L, trainer).getLotId());
    }

    @Test
    @DisplayName("A trainer cannot open another trainer's lot (reported as not found)")
    void testGetLot_TrainerOtherLot() {
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 888L, 1)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> lotService.getLot(10L, trainer));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("A groom can open a lot only if they lead a horse in it")
    void testGetLot_Groom() {
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 1)));
        when(workoutRepository.existsByLotIdAndAssignedToIdAndStatusNot(10L, 4L, WorkoutStatus.CANCELLED))
                .thenReturn(false);

        assertThrows(RuntimeException.class, () -> lotService.getLot(10L, groom));
    }

    @Test
    @DisplayName("An owner can open a lot with their horse and sees only their own horse names")
    void testGetLot_OwnerWithHorseInLot() {
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 4)));
        when(workoutRepository.existsActiveWorkoutOfOwnerInLot(10L, 5L)).thenReturn(true);
        when(lotRepository.findActiveHorseNamesOfOwner(anyCollection(), eq(5L)))
                .thenReturn(List.of(new LotHorseNameRow(10L, "My Horse")));

        TrainingLotResponse res = lotService.getLot(10L, owner);

        assertEquals(List.of("My Horse"), res.getHorseNames());
        assertEquals(4, res.getOccupied());
    }

    @Test
    @DisplayName("An owner without a horse in the lot cannot open it")
    void testGetLot_OwnerWithoutHorseInLot() {
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 4)));
        when(workoutRepository.existsActiveWorkoutOfOwnerInLot(10L, 5L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> lotService.getLot(10L, owner));
        verify(lotRepository, never()).findActiveHorseNamesOfOwner(any(), any());
    }

    @Test
    @DisplayName("A manager can open any lot; an unknown lot is not found")
    void testGetLot_ManagerAndUnknown() {
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 0)));
        when(lotRepository.findLotRowById(404L)).thenReturn(Optional.empty());
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of());

        assertEquals(10L, lotService.getLot(10L, manager).getLotId());
        assertThrows(RuntimeException.class, () -> lotService.getLot(404L, manager));
    }

    // =================================================================
    // rescheduleLot / cancelLot
    // =================================================================

    private TrainingLot lot(Long id, Long trainerId, LotStatus status) {
        TrainingLot lot = new TrainingLot();
        lot.setId(id);
        lot.setTrainerId(trainerId);
        lot.setSubjectId(1L);
        lot.setLotDate(from);
        lot.setStartTime(LocalTime.of(6, 0));
        lot.setEndTime(LocalTime.of(7, 0));
        lot.setStatus(status);
        return lot;
    }

    private Subject subject(int durationMinutes) {
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setDurationMinutes(durationMinutes);
        return subject;
    }

    @Test
    @DisplayName("Rescheduling moves the lot and returns the refreshed response")
    void testRescheduleLot_Success() {
        TrainingLot lot = lot(10L, 9L, LotStatus.SCHEDULED);
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(60)));
        when(lotRepository.findActiveLotsOfDay(9L, from)).thenReturn(List.of(lot));
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 2)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of());

        TrainingLotResponse res = lotService.rescheduleLot(10L, LocalTime.of(8, 0), 9L);

        assertEquals(LocalTime.of(8, 0), lot.getStartTime());
        assertEquals(LocalTime.of(9, 0), lot.getEndTime());
        assertEquals(10L, res.getLotId());
        verify(lotRepository).save(lot);
    }

    @Test
    @DisplayName("Rescheduling outside the golden window is rejected")
    void testRescheduleLot_OutsideGoldenWindow() {
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot(10L, 9L, LotStatus.SCHEDULED)));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(60)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lotService.rescheduleLot(10L, LocalTime.of(9, 30), 9L));
        assertTrue(ex.getMessage().contains("outside the golden window"));
        verify(lotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rescheduling onto another lot of the same trainer is rejected (BR-02)")
    void testRescheduleLot_OverlapsAnotherLot() {
        TrainingLot lot = lot(10L, 9L, LotStatus.SCHEDULED);
        TrainingLot other = lot(11L, 9L, LotStatus.SCHEDULED);
        other.setStartTime(LocalTime.of(8, 0));
        other.setEndTime(LocalTime.of(9, 0));
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(60)));
        when(lotRepository.findActiveLotsOfDay(9L, from)).thenReturn(List.of(lot, other));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lotService.rescheduleLot(10L, LocalTime.of(8, 30), 9L));
        assertTrue(ex.getMessage().contains("overlaps your lot #11"));
    }

    @Test
    @DisplayName("Only the trainer in charge can reschedule or cancel a lot")
    void testRescheduleAndCancel_WrongTrainer() {
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot(10L, 888L, LotStatus.SCHEDULED)));

        assertThrows(AccessDeniedException.class, () -> lotService.rescheduleLot(10L, LocalTime.of(8, 0), 9L));
        assertThrows(AccessDeniedException.class, () -> lotService.cancelLot(10L, 9L));
    }

    @Test
    @DisplayName("A cancelled or completed lot cannot be rescheduled")
    void testRescheduleLot_AlreadyClosed() {
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot(10L, 9L, LotStatus.COMPLETED)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lotService.rescheduleLot(10L, LocalTime.of(8, 0), 9L));
        assertTrue(ex.getMessage().contains("already COMPLETED"));
    }

    @Test
    @DisplayName("Cancelling a lot cancels only its SCHEDULED workouts")
    void testCancelLot_CancelsOnlyScheduledWorkouts() {
        TrainingLot lot = lot(10L, 9L, LotStatus.SCHEDULED);
        TrainingWorkout scheduled = new TrainingWorkout();
        scheduled.setStatus(WorkoutStatus.SCHEDULED);
        TrainingWorkout completed = new TrainingWorkout();
        completed.setStatus(WorkoutStatus.COMPLETED);
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot));
        when(workoutRepository.findByLotId(10L)).thenReturn(List.of(scheduled, completed));
        when(lotRepository.findLotRowById(10L)).thenReturn(Optional.of(row(10L, 9L, 0)));
        when(lotRepository.findActiveHorseNames(anyCollection())).thenReturn(List.of());

        lotService.cancelLot(10L, 9L);

        assertEquals(WorkoutStatus.CANCELLED, scheduled.getStatus());
        assertEquals(WorkoutStatus.COMPLETED, completed.getStatus(), "History is never rewritten");
        assertEquals(LotStatus.CANCELLED, lot.getStatus());
        verify(workoutRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("cancelLotIfEmpty cancels a lot only when no active workout is left")
    void testCancelLotIfEmpty() {
        TrainingLot emptyLot = lot(10L, 9L, LotStatus.SCHEDULED);
        when(workoutRepository.countByLotIdAndStatusNot(10L, WorkoutStatus.CANCELLED)).thenReturn(0L);
        when(workoutRepository.countByLotIdAndStatusNot(11L, WorkoutStatus.CANCELLED)).thenReturn(2L);
        when(lotRepository.findById(10L)).thenReturn(Optional.of(emptyLot));

        lotService.cancelLotIfEmpty(10L);
        lotService.cancelLotIfEmpty(11L);

        assertEquals(LotStatus.CANCELLED, emptyLot.getStatus());
        verify(lotRepository, never()).findById(11L);
    }
}
