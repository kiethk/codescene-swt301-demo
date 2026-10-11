package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.WorkoutStatus;

import java.time.LocalTime;

/**
 * Một buổi tập trong ngày của Groom, đã JOIN sẵn tên ngựa và tên bài tập.
 * (One workout in a groom's day, with horse and subject names already joined.)
 */
public record GroomWorkoutRow(Long workoutId,
                              Long horseId,
                              String horseName,
                              WorkoutStatus status,
                              LocalTime startTime,
                              LocalTime endTime,
                              String subjectName) {
}
