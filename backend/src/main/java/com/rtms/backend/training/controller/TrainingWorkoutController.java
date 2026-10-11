package com.rtms.backend.training.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.CompleteWorkoutRequest;
import com.rtms.backend.training.dto.PlanWorkoutItemResponse;
import com.rtms.backend.training.service.TrainingWorkoutService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workouts")
public class TrainingWorkoutController {

    private final TrainingWorkoutService workoutService;

    public TrainingWorkoutController(TrainingWorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    /**
     * Trainer đóng buổi tập và ghi nhận chỉ số chuyên môn.
     * (The trainer closes a workout and records professional metrics.)
     */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TRAINING_WORKOUT_UPDATE')")
    public ApiResponse<PlanWorkoutItemResponse> completeWorkout(
            @PathVariable Long id,
            @RequestBody CompleteWorkoutRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(workoutService.completeWorkout(id, request, currentUser));
    }
}
