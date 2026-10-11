package com.rtms.backend.training.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.training.dto.TrainingRulesResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TrainingRulesControllerTest {

    @Test
    @DisplayName("The rules published to the UI are the ones the backend enforces")
    void publishesTheEnforcedRules() {
        ApiResponse<TrainingRulesResponse> response = new TrainingRulesController().getRules();

        TrainingRulesResponse rules = response.getData();
        assertEquals(LocalTime.of(6, 0), rules.goldenHoursStart());
        assertEquals(LocalTime.of(10, 0), rules.goldenHoursEnd());
        assertEquals(240, rules.goldenWindowMinutes());
        assertEquals(6, rules.lotCapacity());
        assertEquals(15, rules.minSubjectDurationMinutes());
        assertEquals(240, rules.maxSubjectDurationMinutes());
        assertEquals(220, rules.maxHeartRateLimit());
        assertEquals(100, rules.recoveryHeartRateLimit());
    }
}
