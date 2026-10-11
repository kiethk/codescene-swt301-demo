package com.rtms.backend.training.dto;

/** Một chiến mã trong khu của Trainer. (A horse in the trainer's areas.) */
public record DashboardHorseRow(Long horseId, String horseName, String breed, String stallCode) {
}
