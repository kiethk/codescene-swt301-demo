package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.SurfaceType;

import java.math.BigDecimal;

/**
 * Một buổi tập đã hoàn thành dùng để suy ra sở trường: chỉ gồm các cột cần thiết.
 * (A completed workout used to infer the specialty: only the columns that are needed.)
 *
 * distanceMeters là cự ly thực tế của buổi tập, nếu thiếu thì lấy cự ly mục tiêu của bài tập.
 * (distanceMeters is the actual workout distance, falling back to the subject target distance.)
 */
public record SpecialtyWorkoutRow(Long horseId,
                                  BigDecimal distanceMeters,
                                  SurfaceType surfaceType,
                                  Integer performanceRating,
                                  BigDecimal topSpeedKmh) {
}
