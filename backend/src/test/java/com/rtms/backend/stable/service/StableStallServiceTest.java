package com.rtms.backend.stable.service;
import com.rtms.backend.training.service.TrainingWorkoutService;


import com.rtms.backend.config.FarmSchedulePolicy;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.identity.entity.Role;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StableStallServiceTest {

    @Mock
    private StableStallRepository stableStallRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private TrainingWorkoutService workoutService;

    private StableStallService stableStallService;

    @BeforeEach
    void setUp() {
        stableStallService = new StableStallService(
                stableStallRepository, userRepository, horseRepository, workoutService);
    }

    /** Dựng một Groom hợp lệ để dùng lại giữa các test. */
    private User groom(Long id, String name) {
        Role role = new Role();
        role.setName("GROOM");
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setRole(role);
        return u;
    }

    @Test
    @DisplayName("BR-06: Gán chuồng cho Groom thành công khi chưa vượt quá 3 chuồng")
    void testAssignGroom_Success() {
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(null);

        Role groomRole = new Role();
        groomRole.setName("GROOM");

        User groom = new User();
        groom.setId(5L);
        groom.setFullName("Nguyen Van Groom");
        groom.setRole(groomRole);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(userRepository.findById(5L)).thenReturn(Optional.of(groom));
        when(stableStallRepository.countByGroomId(5L)).thenReturn(2L); // currently 2 < 3
        when(stableStallRepository.save(any(StableStall.class))).thenAnswer(inv -> inv.getArgument(0));

        StableStall result = stableStallService.assignGroom(10L, 5L);

        assertNotNull(result);
        assertEquals(5L, result.getGroomId());
        verify(stableStallRepository).save(stall);
    }

    @Test
    @DisplayName("BR-06: Chặn khi Groom đã phụ trách đủ 3 chuồng")
    void testAssignGroom_ExceedsCapacity_ThrowsException() {
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(null);

        Role groomRole = new Role();
        groomRole.setName("GROOM");

        User groom = new User();
        groom.setId(5L);
        groom.setFullName("Nguyen Van Groom");
        groom.setRole(groomRole);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(userRepository.findById(5L)).thenReturn(Optional.of(groom));
        when(stableStallRepository.countByGroomId(5L)).thenReturn((long) FarmSchedulePolicy.MAX_STALLS_PER_GROOM);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> stableStallService.assignGroom(10L, 5L));

        assertTrue(ex.getMessage().contains("already looks after 3 stalls"));
        verify(stableStallRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chặn khi gán người dùng không có role GROOM")
    void testAssignGroom_NotGroom_ThrowsException() {
        StableStall stall = new StableStall();
        stall.setId(10L);

        Role trainerRole = new Role();
        trainerRole.setName("TRAINER");

        User trainer = new User();
        trainer.setId(8L);
        trainer.setFullName("Tran Van Trainer");
        trainer.setRole(trainerRole);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(userRepository.findById(8L)).thenReturn(Optional.of(trainer));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> stableStallService.assignGroom(10L, 8L));

        assertTrue(ex.getMessage().contains("not GROOM"));
        verify(stableStallRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gỡ Groom khỏi chuồng (groomId = null) thành công")
    void testAssignGroom_Unassign_Success() {
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(5L);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(stableStallRepository.save(any(StableStall.class))).thenAnswer(inv -> inv.getArgument(0));

        StableStall result = stableStallService.assignGroom(10L, null);

        assertNotNull(result);
        assertNull(result.getGroomId());
        verify(stableStallRepository).save(stall);
    }

    // =================================================================
    // ĐỒNG BỘ GROOM CHO BUỔI TẬP TƯƠNG LAI
    // =================================================================

    @Test
    @DisplayName("Đổi Groom: buổi tập tương lai của ngựa trong chuồng được chuyển sang Groom mới")
    void testAssignGroom_ReassignsFutureWorkouts() {
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(4L);

        Horse horse = new Horse();
        horse.setId(77L);
        horse.setCurrentStallId(10L);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(userRepository.findById(5L)).thenReturn(Optional.of(groom(5L, "Groom B")));
        when(stableStallRepository.countByGroomId(5L)).thenReturn(1L);
        when(horseRepository.findByCurrentStallId(10L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.save(any(StableStall.class))).thenAnswer(i -> i.getArgument(0));

        stableStallService.assignGroom(10L, 5L);

        // Buổi tập phải được chuyển sang ĐÚNG Groom mới, không phải Groom cũ
        verify(workoutService).reassignFutureWorkoutsToGroom(77L, 5L);
    }

    @Test
    @DisplayName("Gỡ Groom: buổi tập tương lai chuyển sang 'chưa phân công' (null)")
    void testAssignGroom_Unassign_SetsWorkoutsToNull() {
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(4L);

        Horse horse = new Horse();
        horse.setId(77L);
        horse.setCurrentStallId(10L);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(horseRepository.findByCurrentStallId(10L)).thenReturn(Optional.of(horse));
        when(stableStallRepository.save(any(StableStall.class))).thenAnswer(i -> i.getArgument(0));

        stableStallService.assignGroom(10L, null);

        verify(workoutService).reassignFutureWorkoutsToGroom(eq(77L), isNull());
    }

    @Test
    @DisplayName("Chuồng trống: không gọi đồng bộ buổi tập vì không có ngựa nào")
    void testAssignGroom_EmptyStall_NoReassign() {
        StableStall stall = new StableStall();
        stall.setId(10L);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(userRepository.findById(5L)).thenReturn(Optional.of(groom(5L, "Groom B")));
        when(stableStallRepository.countByGroomId(5L)).thenReturn(0L);
        when(horseRepository.findByCurrentStallId(10L)).thenReturn(Optional.empty());
        when(stableStallRepository.save(any(StableStall.class))).thenAnswer(i -> i.getArgument(0));

        stableStallService.assignGroom(10L, 5L);

        verify(workoutService, never()).reassignFutureWorkoutsToGroom(any(), any());
    }

    @Test
    @DisplayName("BR-09: vướng xung đột lot thì KHÔNG ghi Groom mới vào chuồng")
    void testAssignGroom_Br09Conflict_DoesNotSaveStall() {
        StableStall stall = new StableStall();
        stall.setId(10L);
        stall.setGroomId(4L);

        Horse horse = new Horse();
        horse.setId(77L);
        horse.setCurrentStallId(10L);

        when(stableStallRepository.findById(10L)).thenReturn(Optional.of(stall));
        when(userRepository.findById(5L)).thenReturn(Optional.of(groom(5L, "Groom B")));
        when(stableStallRepository.countByGroomId(5L)).thenReturn(1L);
        when(horseRepository.findByCurrentStallId(10L)).thenReturn(Optional.of(horse));
        doThrow(new IllegalStateException("Groom này đang dắt 'Red Fox'"))
                .when(workoutService).reassignFutureWorkoutsToGroom(77L, 5L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> stableStallService.assignGroom(10L, 5L));

        assertTrue(ex.getMessage().contains("Red Fox"));
        // Đồng bộ phải chạy TRƯỚC khi ghi -> ném lỗi thì chuồng giữ Groom cũ
        verify(stableStallRepository, never()).save(any());
        assertEquals(4L, stall.getGroomId());
    }
}