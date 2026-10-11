package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.TrainingPlanStatus;

import java.time.LocalDate;

/**
 * Một kế hoạch của Trainer kèm tên khóa và số buổi đã hoàn thành (tính bằng SQL).
 * (A trainer's plan with course name and completed-session count, computed in SQL.)
 */
public record DashboardPlanRow(Long planId,
                               Long horseId,
                               TrainingPlanStatus status,
                               LocalDate startDate,
                               LocalDate endDate,
                               String courseName,
                               Integer totalSessions,
                               Long completedSessions) {
}
