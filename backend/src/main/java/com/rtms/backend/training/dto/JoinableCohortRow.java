package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.TrainingDay;

import java.time.LocalDate;
import java.util.Set;

/** Một nhóm có thể ghi danh chung. (A cohort that new horses can join.) */
public record JoinableCohortRow(LocalDate startDate, Set<TrainingDay> trainingDays, Long horseCount) {
}
