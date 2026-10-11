package com.rtms.backend.stable.service;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.stable.dto.TodayTaskItemResponse;
import com.rtms.backend.stable.enums.TaskSource;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.entity.TrainingWorkout;
import com.rtms.backend.training.enums.WorkoutStatus;


import com.rtms.backend.config.FarmSchedulePolicy;
import com.rtms.backend.stable.entity.GroomDailyTask;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.stable.enums.GroomTaskType;
import com.rtms.backend.stable.enums.SopSlot;
import com.rtms.backend.stable.repository.GroomDailyTaskRepository;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import com.rtms.backend.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroomDailyTaskServiceTest {

    @Mock
    private GroomDailyTaskRepository taskRepository;

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StableStallRepository stableStallRepository;

    @Mock
    private TrainingWorkoutRepository workoutRepository;

    @Mock
    private CareScheduleRepository careScheduleRepository;

    private GroomDailyTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new GroomDailyTaskService(
                taskRepository,
                horseRepository,
                userRepository,
                stableStallRepository,
                workoutRepository,
                careScheduleRepository
        );
    }

    @Test
    @DisplayName("1. Sinh việc thành công cho ngựa trong chuồng - Đủ bộ việc chuẩn SOP")
    void testGenerateDailyRoutineTasks_Success() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 9, 22);

        Horse horse = new Horse();
        horse.setId(1L);
        horse.setName("Lightning Bolt");
        horse.setCurrentStallId(10L);

        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(4L);

        when(horseRepository.findByCurrentStallIdIsNotNull()).thenReturn(List.of(horse));
        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(taskRepository.existsByHorseIdAndTaskTypeAndScheduledTimeBetween(
                anyLong(), any(), any(), any())).thenReturn(false);
        when(taskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<GroomDailyTask> result = taskService.generateDailyRoutineTasks(targetDate);

        // Assert — số lượng bám theo enum, không hardcode con số 5
        assertEquals(SopSlot.values().length, result.size(),
                "Phải sinh đúng một task cho mỗi mốc SOP");

        // Assert — từng mốc khớp enum. Đổi giờ trong SopSlot thì test tự đúng theo.
        SopSlot[] slots = SopSlot.values();
        for (int i = 0; i < slots.length; i++) {
            GroomDailyTask task = result.get(i);
            SopSlot slot = slots[i];

            assertEquals(4L, task.getGroomId(),
                    "Task phải thuộc về Groom phụ trách chuồng");
            assertEquals(1L, task.getHorseId());
            assertEquals(slot.getTaskType(), task.getTaskType(),
                    "Sai loại việc ở mốc " + slot.name());
            assertEquals(targetDate.atTime(slot.getTime()), task.getScheduledTime(),
                    "Sai giờ ở mốc " + slot.name());
            assertEquals(slot.getNote(), task.getNotes());
            assertEquals(Boolean.FALSE, task.getIsCompleted(),
                    "Task mới sinh phải ở trạng thái chưa hoàn thành");
        }
    }

    @Test
    @DisplayName("1b. Không mốc SOP nào được rơi vào khung giờ vàng huấn luyện")
    void testSopSlotsDoNotCollideWithGoldenHours() {
        for (SopSlot slot : SopSlot.values()) {
            boolean insideGoldenHours =
                    !slot.getTime().isBefore(FarmSchedulePolicy.GOLDEN_HOURS_START)
                 && slot.getTime().isBefore(FarmSchedulePolicy.GOLDEN_HOURS_END);

            assertFalse(insideGoldenHours, String.format(
                    "Mốc SOP %s (%s) rơi vào khung giờ vàng %s–%s — Groom sẽ bị kẹt "
                  + "giữa việc chuồng trại và việc dắt ngựa ra sân!",
                    slot.name(), slot.getTime(),
                    FarmSchedulePolicy.GOLDEN_HOURS_START,
                    FarmSchedulePolicy.GOLDEN_HOURS_END));
        }
    }

    @Test
    @DisplayName("2. BR-08 — Không sinh trùng nếu task đã tồn tại trong cửa sổ ±30 phút")
    void testGenerateDailyRoutineTasks_Idempotent() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2026, 9, 22);

        Horse horse = new Horse();
        horse.setId(1L);
        horse.setCurrentStallId(10L);

        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(4L);

        when(horseRepository.findByCurrentStallIdIsNotNull()).thenReturn(List.of(horse));
        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(taskRepository.existsByHorseIdAndTaskTypeAndScheduledTimeBetween(
                anyLong(), any(), any(), any())).thenReturn(true);

        // Act
        List<GroomDailyTask> result = taskService.generateDailyRoutineTasks(targetDate);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size(), "Không được sinh trùng lặp nếu task đã tồn tại");
        verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("3. Không có ngựa nào trong chuồng - Trả về mảng rỗng")
    void testGenerateDailyRoutineTasks_NoHorsesInStalls() {
        // Arrange
        when(horseRepository.findByCurrentStallIdIsNotNull()).thenReturn(Collections.emptyList());

        // Act
        List<GroomDailyTask> result = taskService.generateDailyRoutineTasks(LocalDate.now());

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("4. Ngựa có chuồng nhưng chuồng chưa gán Groom - Bỏ qua")
    void testGenerateDailyRoutineTasks_StallWithoutGroom() {
        // Arrange
        Horse horse = new Horse();
        horse.setId(1L);
        horse.setCurrentStallId(10L);

        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(null); // Chưa gán Groom

        when(horseRepository.findByCurrentStallIdIsNotNull()).thenReturn(List.of(horse));
        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));

        // Act
        List<GroomDailyTask> result = taskService.generateDailyRoutineTasks(LocalDate.now());

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("5. Aggregator: Gom việc từ 3 nguồn (SOP, Workout, Thú y) và sắp xếp đúng thứ tự thời gian")
    void testTodayAggregatedTasks_ThreeSourcesSortedChronologically() {
        LocalDate targetDate = LocalDate.of(2026, 10, 5);
        com.rtms.backend.security.AuthenticatedUser groomUser =
                new com.rtms.backend.security.AuthenticatedUser(4L, "groom@example.com", "GROOM");

        // Nguồn 1: SOP task lúc 05:00
        GroomDailyTask sopTask = new GroomDailyTask(4L, 1L, GroomTaskType.FEEDING,
                targetDate.atTime(5, 0), "Cho ăn sáng");
        sopTask.setId(101L);
        sopTask.setIsCompleted(false);

        when(taskRepository.findByGroomIdAndScheduledTimeBetween(eq(4L), any(), any()))
                .thenReturn(List.of(sopTask));

        // Source 2: a workout joined with its lot at 06:00 (horse and subject names come in the same row)
        com.rtms.backend.training.dto.GroomWorkoutRow workoutRow = new com.rtms.backend.training.dto.GroomWorkoutRow(
                201L, 1L, "Silver Moon", com.rtms.backend.training.enums.WorkoutStatus.SCHEDULED,
                java.time.LocalTime.of(6, 0), java.time.LocalTime.of(7, 30), "Steady 1200m run");

        when(workoutRepository.findGroomWorkoutRows(4L, targetDate)).thenReturn(List.of(workoutRow));

        // Nguồn 3: Stall + Thú y lúc 13:30 (VET_WINDOW_START)
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(4L);
        when(stableStallRepository.findByGroomId(4L)).thenReturn(List.of(stall));

        Horse horse1 = new Horse();
        horse1.setId(1L);
        horse1.setName("Silver Moon");
        horse1.setCurrentStallId(10L);
        when(horseRepository.findByCurrentStallIdIn(List.of(10L))).thenReturn(List.of(horse1));

        com.rtms.backend.medical.entity.CareSchedule schedule = new com.rtms.backend.medical.entity.CareSchedule();
        schedule.setId(301L);
        schedule.setHorseId(1L);
        schedule.setCareType(com.rtms.backend.medical.enums.CareType.ROUTINE);
        schedule.setScheduledAt(targetDate.atTime(FarmSchedulePolicy.VET_WINDOW_START));
        schedule.setDescription("Tiêm định kỳ 6 tháng");
        schedule.setStatus(com.rtms.backend.medical.enums.CareScheduleStatus.SCHEDULED);

        when(careScheduleRepository.findByHorseIdInAndScheduledAtBetweenAndStatusIn(eq(List.of(1L)), any(), any(), any()))
                .thenReturn(List.of(schedule));

        // Act
        List<com.rtms.backend.stable.dto.TodayTaskItemResponse> items =
                taskService.getTodayAggregatedTasks(null, targetDate, groomUser);

        // Assert
        assertEquals(3, items.size());

        // Item 1: SOP 05:00
        assertEquals(com.rtms.backend.stable.enums.TaskSource.SOP, items.get(0).getSource());
        assertEquals(java.time.LocalTime.of(5, 0), items.get(0).getStartTime());
        assertEquals("Silver Moon", items.get(0).getHorseName());
        assertTrue(items.get(0).isActionable());

        // Item 2: WORKOUT 06:00
        assertEquals(com.rtms.backend.stable.enums.TaskSource.WORKOUT, items.get(1).getSource());
        assertEquals(java.time.LocalTime.of(6, 0), items.get(1).getStartTime());
        assertEquals(java.time.LocalTime.of(7, 30), items.get(1).getEndTime());
        assertTrue(items.get(1).getTitle().contains("Steady 1200m run"));
        assertFalse(items.get(1).isActionable());

        // Item 3: PREVENTIVE_CARE 13:30
        assertEquals(com.rtms.backend.stable.enums.TaskSource.PREVENTIVE_CARE, items.get(2).getSource());
        assertEquals(FarmSchedulePolicy.VET_WINDOW_START, items.get(2).getStartTime());
        assertFalse(items.get(2).isActionable());
    }

    @Test
    @DisplayName("6. Groom hoàn thành nhiệm vụ được giao thành công")
    void testCompleteTask_Success() {
        Long taskId = 50L;
        com.rtms.backend.security.AuthenticatedUser groom =
                new com.rtms.backend.security.AuthenticatedUser(4L, "groom@example.com", "GROOM");

        GroomDailyTask task = new GroomDailyTask();
        task.setId(taskId);
        task.setGroomId(4L);
        task.setIsCompleted(false);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(GroomDailyTask.class))).thenAnswer(inv -> inv.getArgument(0));

        GroomDailyTask result = taskService.completeTask(taskId, groom);

        assertNotNull(result);
        assertTrue(result.getIsCompleted());
        assertNotNull(result.getCompletedAt());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("7. Groom khác không được phép xác nhận hoàn thành nhiệm vụ của Groom phụ trách")
    void testCompleteTask_WrongGroom_ThrowsAccessDenied() {
        Long taskId = 50L;
        com.rtms.backend.security.AuthenticatedUser otherGroom =
                new com.rtms.backend.security.AuthenticatedUser(99L, "other@example.com", "GROOM");

        GroomDailyTask task = new GroomDailyTask();
        task.setId(taskId);
        task.setGroomId(4L); // Thuộc groom 4L

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> taskService.completeTask(taskId, otherGroom));
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. Không tìm thấy nhiệm vụ theo ID -> Ném RuntimeException")
    void testCompleteTask_NotFound_ThrowsRuntimeException() {
        Long taskId = 999L;
        com.rtms.backend.security.AuthenticatedUser groom =
                new com.rtms.backend.security.AuthenticatedUser(4L, "groom@example.com", "GROOM");

        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> taskService.completeTask(taskId, groom));
    }
}

