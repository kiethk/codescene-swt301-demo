package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.LotStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một lot kèm tên bài tập, thời lượng và số ngựa còn hiệu lực (tính bằng SQL).
 * (A lot with subject name, duration and active-horse count, computed in SQL.)
 */
public record TrainingLotRow(Long lotId,
                             Long trainerId,
                             LocalDate lotDate,
                             LocalTime startTime,
                             LocalTime endTime,
                             Long subjectId,
                             String subjectName,
                             Integer durationMinutes,
                             LotStatus status,
                             Integer maxCapacity,
                             Long occupied) {
}
