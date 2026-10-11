package com.rtms.backend.training.service;

import com.rtms.backend.config.ApiException;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.enums.HorseStatus;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.horse.repository.HorseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingDecisionServiceTest {

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private CareScheduleRepository careScheduleRepository;

    @Mock
    private TrainingWorkoutService workoutService;

    private TrainingDecisionService service;
    private Horse horse;

    @BeforeEach
    void setUp() {
        service = new TrainingDecisionService(horseRepository, careScheduleRepository, workoutService);
        horse = new Horse();
        horse.setId(7L);
        horse.setName("Thunder");
        horse.setCurrentStatus(HorseStatus.ELIGIBLE);
        horse.setTrainingDecision(TrainingDecision.ALLOWED);
    }

    @Test
    @DisplayName("block: saves BLOCKED + reason and cancels future workouts")
    void block_setsDecisionAndCancelsFutureTraining() {
        service.block(horse, "Tendon strain");

        assertEquals(TrainingDecision.BLOCKED, horse.getTrainingDecision());
        assertEquals("Tendon strain", horse.getTrainingDecisionReason());
        verify(horseRepository).save(horse);
        verify(workoutService).cancelFutureTrainingForHorse(7L, "Horse is blocked from training: Tendon strain");
    }

    @Test
    @DisplayName("allow: back to ALLOWED, clears the reason, leaves plans untouched")
    void allow_clearsReason() {
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        horse.setTrainingDecisionReason("Tendon strain");

        service.allow(horse);

        assertEquals(TrainingDecision.ALLOWED, horse.getTrainingDecision());
        assertNull(horse.getTrainingDecisionReason());
        verify(horseRepository).save(horse);
        verifyNoInteractions(workoutService);
    }

    @Test
    @DisplayName("assertCanTrain: an ELIGIBLE + ALLOWED horse passes")
    void assertCanTrain_eligibleAndAllowed_passes() {
        assertDoesNotThrow(() -> service.assertCanTrain(horse));
    }

    @Test
    @DisplayName("assertCanTrain: a horse not admitted yet (CANDIDATE) -> 409 HORSE_NOT_ELIGIBLE")
    void assertCanTrain_candidate_rejected() {
        horse.setCurrentStatus(HorseStatus.CANDIDATE);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertCanTrain(horse));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("HORSE_NOT_ELIGIBLE", ex.getErrorCode());
    }

    @Test
    @DisplayName("assertCanTrain: blocked -> 409 TRAINING_BLOCKED with the reason and re-exam time")
    void assertCanTrain_blocked_showsReasonAndNextExam() {
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        horse.setTrainingDecisionReason("Tendon strain");
        when(careScheduleRepository.findNextExamTime(eq(7L), anyList()))
                .thenReturn(LocalDateTime.of(2026, 10, 20, 14, 0));

        ApiException ex = assertThrows(ApiException.class, () -> service.assertCanTrain(horse));

        assertEquals("TRAINING_BLOCKED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Tendon strain"));
        assertTrue(ex.getMessage().contains("re-examination at 20/10/2026 14:00"));
    }

    @Test
    @DisplayName("assertCanTrain: blocked with no exam left -> reports no follow-up exam scheduled")
    void assertCanTrain_blockedWithoutNextExam() {
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        when(careScheduleRepository.findNextExamTime(eq(7L), anyList())).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> service.assertCanTrain(horse));

        assertTrue(ex.getMessage().contains("no follow-up exam scheduled"));
        assertTrue(ex.getMessage().contains("as directed by the vet"));
    }
}
