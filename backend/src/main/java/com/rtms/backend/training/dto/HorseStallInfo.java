package com.rtms.backend.training.dto;

/**
 * Chuồng và khu của một chiến mã, lấy bằng một câu JOIN.
 * (A horse's stall and area, fetched with a single JOIN.)
 */
public record HorseStallInfo(Long horseId,
                             String stallCode,
                             Long stallGroomId,
                             String areaCode,
                             Long areaTrainerId) {
}
