package com.rtms.backend.training.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một lot vướng BR-09 khi đổi Groom: Groom mới đã dắt một con ngựa khác trong lot đó.
 * (A lot violating BR-09 on groom change: the new groom already leads another horse there.)
 */
public record GroomConflictRow(Long lotId, LocalDate lotDate, LocalTime startTime, String otherHorseName) {
}
