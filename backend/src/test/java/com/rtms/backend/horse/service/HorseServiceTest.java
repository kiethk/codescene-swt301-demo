package com.rtms.backend.horse.service;
import com.rtms.backend.horse.enums.HorseStatus;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.stable.entity.Area;
import com.rtms.backend.stable.repository.AreaRepository;
import com.rtms.backend.training.service.TrainingWorkoutService;


import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.stable.enums.StallStatus;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HorseServiceTest {

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private StableStallRepository stableStallRepository;

    @Mock
    private TrainingWorkoutService workoutService;

    @Mock
    private com.rtms.backend.stable.repository.AreaRepository areaRepository;

    private HorseService horseService;

    @BeforeEach
    void setUp() {
        horseService = new HorseService(horseRepository, stableStallRepository, workoutService, areaRepository);
    }

    @Test
    @DisplayName("Gán chuồng thành công: Chuồng mới chuyển sang OCCUPIED, ngựa nhận currentStallId")
    void testAssignStall_Success() {
        Horse horse = new Horse();
        horse.setId(1L);
        horse.setName("Lightning Bolt");
        horse.setCurrentStallId(null);

        StableStall stall = new StableStall();
        stall.setId(6L);
        stall.setStatus(StallStatus.AVAILABLE);

        when(horseRepository.findById(1L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(6L)).thenReturn(Optional.of(stall));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        Horse updated = horseService.assignStall(1L, 6L);

        assertNotNull(updated);
        assertEquals(6L, updated.getCurrentStallId());
        assertEquals(StallStatus.OCCUPIED, stall.getStatus());
        verify(stableStallRepository, times(1)).save(stall);
        verify(horseRepository, times(1)).save(horse);
    }

    @Test
    @DisplayName("Đổi chuồng: Chuồng cũ về AVAILABLE, chuồng mới thành OCCUPIED")
    void testAssignStall_ChangeStall() {
        Horse horse = new Horse();
        horse.setId(1L);
        horse.setCurrentStallId(5L); // Chuồng cũ

        StableStall oldStall = new StableStall();
        oldStall.setId(5L);
        oldStall.setStatus(StallStatus.OCCUPIED);

        StableStall newStall = new StableStall();
        newStall.setId(6L);
        newStall.setStatus(StallStatus.AVAILABLE);

        when(horseRepository.findById(1L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(6L)).thenReturn(Optional.of(newStall));
        when(stableStallRepository.findById(5L)).thenReturn(Optional.of(oldStall));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        Horse updated = horseService.assignStall(1L, 6L);

        assertEquals(6L, updated.getCurrentStallId());
        assertEquals(StallStatus.AVAILABLE, oldStall.getStatus());
        assertEquals(StallStatus.OCCUPIED, newStall.getStatus());
        verify(stableStallRepository, times(1)).save(oldStall);
        verify(stableStallRepository, times(1)).save(newStall);
    }

    @Test
    @DisplayName("Lỗi khi chuồng không tồn tại")
    void testAssignStall_StallNotFound() {
        Horse horse = new Horse();
        horse.setId(1L);

        when(horseRepository.findById(1L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> horseService.assignStall(1L, 999L));
        verify(horseRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllHorses với mine=true và vai trò HEAD_TRAINER: chỉ trả ngựa ở khu Trainer phụ trách")
    void testGetAllHorses_HeadTrainer_Mine() {
        com.rtms.backend.security.AuthenticatedUser trainer =
                new com.rtms.backend.security.AuthenticatedUser(10L, "trainer@test.com", "HEAD_TRAINER");

        com.rtms.backend.stable.entity.Area myArea = new com.rtms.backend.stable.entity.Area();
        myArea.setId(1L);
        myArea.setTrainerId(10L);

        com.rtms.backend.stable.entity.Area otherArea = new com.rtms.backend.stable.entity.Area();
        otherArea.setId(2L);
        otherArea.setTrainerId(99L);

        when(areaRepository.findAll()).thenReturn(List.of(myArea, otherArea));

        StableStall stall1 = new StableStall();
        stall1.setId(101L);
        stall1.setAreaId(1L);

        StableStall stall2 = new StableStall();
        stall2.setId(201L);
        stall2.setAreaId(2L);

        when(stableStallRepository.findAll()).thenReturn(List.of(stall1, stall2));

        Horse h1 = new Horse();
        h1.setId(1L);
        h1.setCurrentStallId(101L);
        h1.setCurrentStatus(com.rtms.backend.horse.enums.HorseStatus.ELIGIBLE);

        Horse h2 = new Horse();
        h2.setId(2L);
        h2.setCurrentStallId(201L);
        h2.setCurrentStatus(com.rtms.backend.horse.enums.HorseStatus.ELIGIBLE);

        Horse h3 = new Horse();
        h3.setId(3L);
        h3.setCurrentStallId(null);
        h3.setCurrentStatus(com.rtms.backend.horse.enums.HorseStatus.CANDIDATE);

        when(horseRepository.findAll()).thenReturn(List.of(h1, h2, h3));

        List<Horse> result = horseService.getAllHorses(trainer, true, com.rtms.backend.horse.enums.HorseStatus.ELIGIBLE);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    // =================================================================
    // unassigned = true — ngựa CHƯA xếp chuồng
    // =================================================================

    @Test
    @DisplayName("unassigned=true: chỉ trả ngựa chưa có chuồng, bỏ qua hẳn nhánh mine")
    void testGetAllHorses_Unassigned() {
        com.rtms.backend.security.AuthenticatedUser trainer =
                new com.rtms.backend.security.AuthenticatedUser(2L, "t@x.com", "HEAD_TRAINER");

        Horse placed = new Horse();
        placed.setId(1L);
        placed.setCurrentStallId(101L);

        Horse free = new Horse();
        free.setId(3L);
        free.setCurrentStallId(null);

        when(horseRepository.findAll()).thenReturn(List.of(placed, free));

        List<Horse> result = horseService.getAllHorses(trainer, true, true, null);

        // mine=true cũng được truyền, nhưng unassigned thắng — nếu lồng hai
        // điều kiện lại thì kết quả sẽ rỗng, đúng lỗi màn xếp chuồng đã gặp.
        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getId());
        verify(areaRepository, never()).findAll();
    }

    // =================================================================
    // GỠ NGỰA KHỎI CHUỒNG (stallId = null)
    // =================================================================

    @Test
    @DisplayName("Gỡ ngựa: chuồng cũ về AVAILABLE, currentStallId về null, buổi tập 'chưa phân công'")
    void testAssignStall_Unassign_Success() {
        Horse horse = new Horse();
        horse.setId(15L);
        horse.setName("Dark Star");
        horse.setCurrentStallId(42L);

        StableStall oldStall = new StableStall();
        oldStall.setId(42L);
        oldStall.setStatus(StallStatus.OCCUPIED);

        when(horseRepository.findById(15L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(42L)).thenReturn(Optional.of(oldStall));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        Horse result = horseService.assignStall(15L, null);

        assertNull(result.getCurrentStallId());
        assertEquals(StallStatus.AVAILABLE, oldStall.getStatus());
        verify(workoutService).reassignFutureWorkoutsToGroom(eq(15L), isNull());
    }

    @Test
    @DisplayName("Gỡ ngựa vốn đã không ở chuồng nào: không làm gì, không ghi DB")
    void testAssignStall_Unassign_AlreadyFree_NoOp() {
        Horse horse = new Horse();
        horse.setId(15L);
        horse.setCurrentStallId(null);

        when(horseRepository.findById(15L)).thenReturn(Optional.of(horse));

        Horse result = horseService.assignStall(15L, null);

        assertNull(result.getCurrentStallId());
        verify(horseRepository, never()).save(any());
        verify(workoutService, never()).reassignFutureWorkoutsToGroom(any(), any());
    }

    // =================================================================
    // BR-07 — một chuồng một chiến mã
    // =================================================================

    @Test
    @DisplayName("BR-07: chặn xếp ngựa vào chuồng đang có con khác, kèm tên con đó")
    void testAssignStall_StallOccupied_ThrowsBusinessError() {
        Horse moving = new Horse();
        moving.setId(9L);
        moving.setName("Red Fox");
        moving.setCurrentStallId(18L);

        Horse occupant = new Horse();
        occupant.setId(5L);
        occupant.setName("BOOSTER WINNER");
        occupant.setCurrentStallId(6L);

        StableStall target = new StableStall();
        target.setId(6L);
        target.setStallCode("A1");
        target.setStatus(StallStatus.OCCUPIED);

        when(horseRepository.findById(9L)).thenReturn(Optional.of(moving));
        when(stableStallRepository.findById(6L)).thenReturn(Optional.of(target));
        when(horseRepository.findByCurrentStallId(6L)).thenReturn(Optional.of(occupant));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> horseService.assignStall(9L, 6L));

        assertTrue(ex.getMessage().contains("A1"));
        assertTrue(ex.getMessage().contains("BOOSTER WINNER"));
        verify(horseRepository, never()).save(any());
        verify(workoutService, never()).reassignFutureWorkoutsToGroom(any(), any());
    }

    @Test
    @DisplayName("BR-07: gán lại ĐÚNG chuồng ngựa đang ở thì không bị chặn")
    void testAssignStall_SameStall_Allowed() {
        Horse horse = new Horse();
        horse.setId(5L);
        horse.setName("BOOSTER WINNER");
        horse.setCurrentStallId(6L);

        StableStall stall = new StableStall();
        stall.setId(6L);
        stall.setStallCode("A1");
        stall.setStatus(StallStatus.OCCUPIED);

        when(horseRepository.findById(5L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(6L)).thenReturn(Optional.of(stall));
        when(horseRepository.findByCurrentStallId(6L)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        Horse result = horseService.assignStall(5L, 6L);

        assertEquals(6L, result.getCurrentStallId());
        // Cùng chuồng -> không cần đồng bộ Groom
        verify(workoutService, never()).reassignFutureWorkoutsToGroom(any(), any());
    }

    @Test
    @DisplayName("Chuyển sang chuồng khác: buổi tập tương lai chuyển theo Groom của chuồng MỚI")
    void testAssignStall_DifferentStall_ReassignsToNewStallGroom() {
        Horse horse = new Horse();
        horse.setId(9L);
        horse.setName("Red Fox");
        horse.setCurrentStallId(18L);

        StableStall oldStall = new StableStall();
        oldStall.setId(18L);
        oldStall.setStatus(StallStatus.OCCUPIED);

        StableStall newStall = new StableStall();
        newStall.setId(22L);
        newStall.setStallCode("A5");
        newStall.setGroomId(7L);
        newStall.setStatus(StallStatus.AVAILABLE);

        when(horseRepository.findById(9L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(22L)).thenReturn(Optional.of(newStall));
        when(stableStallRepository.findById(18L)).thenReturn(Optional.of(oldStall));
        when(horseRepository.findByCurrentStallId(22L)).thenReturn(Optional.empty());
        when(horseRepository.save(any(Horse.class))).thenAnswer(i -> i.getArgument(0));

        horseService.assignStall(9L, 22L);

        verify(workoutService).reassignFutureWorkoutsToGroom(9L, 7L);
        assertEquals(StallStatus.AVAILABLE, oldStall.getStatus());
        assertEquals(StallStatus.OCCUPIED, newStall.getStatus());
    }

    @Test
    @DisplayName("BR-09 khi đổi chuồng: vướng xung đột thì KHÔNG ghi gì")
    void testAssignStall_Br09Conflict_DoesNotWrite() {
        Horse horse = new Horse();
        horse.setId(9L);
        horse.setCurrentStallId(18L);

        StableStall newStall = new StableStall();
        newStall.setId(6L);
        newStall.setStallCode("A1");
        newStall.setGroomId(4L);

        when(horseRepository.findById(9L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.findById(6L)).thenReturn(Optional.of(newStall));
        when(horseRepository.findByCurrentStallId(6L)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Groom này đang dắt 'BOOSTER WINNER'"))
                .when(workoutService).reassignFutureWorkoutsToGroom(9L, 4L);

        assertThrows(IllegalStateException.class, () -> horseService.assignStall(9L, 6L));

        verify(horseRepository, never()).save(any());
        assertEquals(18L, horse.getCurrentStallId());
    }
}