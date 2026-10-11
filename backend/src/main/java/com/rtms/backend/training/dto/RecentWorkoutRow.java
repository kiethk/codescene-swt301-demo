package com.rtms.backend.training.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Buổi tập đã hoàn thành gần đây — chỉ gồm các cột mà bộ luật cảnh báo cần.
 * (A recently completed workout — only the columns the alert rules need.)
 */
public record RecentWorkoutRow(Long horseId,
                               LocalDate lotDate,
                               LocalTime lotEndTime,
                               Integer maxHeartRate,
                               Integer recoveryHeartRate,
                               Integer performanceRating) {
}
