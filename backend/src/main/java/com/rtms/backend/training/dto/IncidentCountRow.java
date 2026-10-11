package com.rtms.backend.training.dto;

/** Số báo cáo sự cố của một chiến mã. (Incident-report count for one horse.) */
public record IncidentCountRow(Long horseId, Long count) {
}
