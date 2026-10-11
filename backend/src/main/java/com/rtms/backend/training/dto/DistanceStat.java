package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.DistanceBand;

import java.math.BigDecimal;

/**
 * Thống kê của chiến mã trong một nhóm cự ly: bằng chứng đằng sau một huy hiệu sở trường.
 * (A horse's statistics in one distance band: the evidence behind a specialty badge.)
 *
 * averageTopSpeedKmh chỉ để hiển thị, không dùng để quyết định gắn huy hiệu; null nếu không buổi nào ghi tốc độ.
 * (averageTopSpeedKmh is for display only and never decides a badge; null when no workout recorded a speed.)
 */
public record DistanceStat(DistanceBand band,
                           int workoutCount,
                           BigDecimal averageRating,
                           BigDecimal averageTopSpeedKmh) {
}
