package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.IntensityLevel;
import com.rtms.backend.training.enums.SubjectCategory;
import com.rtms.backend.training.enums.SurfaceType;

import java.math.BigDecimal;

public class CreateSubjectRequest {
    private SubjectCategory category;        // ENDURANCE, SPEED, GATE_START, RECOVERY
    private String name;
    private String description;
    private SurfaceType surfaceType;        // TURF, DIRT, SYNTHETIC
    private BigDecimal targetDistanceMeters;
    private IntensityLevel intensityLevel;     // LOW, MEDIUM, HIGH
    private Integer durationMinutes;              // mặc định 60 nếu null (defaults to 60 when null)

    public SubjectCategory getCategory() { return category; }
    public void setCategory(SubjectCategory category) { this.category = category; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SurfaceType getSurfaceType() { return surfaceType; }
    public void setSurfaceType(SurfaceType surfaceType) { this.surfaceType = surfaceType; }

    public BigDecimal getTargetDistanceMeters() { return targetDistanceMeters; }
    public void setTargetDistanceMeters(BigDecimal targetDistanceMeters) { this.targetDistanceMeters = targetDistanceMeters; }

    public IntensityLevel getIntensityLevel() { return intensityLevel; }
    public void setIntensityLevel(IntensityLevel intensityLevel) { this.intensityLevel = intensityLevel; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
}