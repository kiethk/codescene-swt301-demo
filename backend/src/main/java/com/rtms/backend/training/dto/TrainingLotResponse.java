package com.rtms.backend.training.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TrainingLotResponse {

    private Long lotId;
    private LocalDate lotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long subjectId;
    private String subjectName;
    private Integer durationMinutes;
    private String status;
    private Integer maxCapacity;
    private Integer occupied;
    private Integer remainingSlots;
    private List<String> horseNames;

    public TrainingLotResponse(TrainingLotRow row, List<String> horseNames) {
        int occupied = row.occupied().intValue();
        this.lotId = row.lotId();
        this.lotDate = row.lotDate();
        this.startTime = row.startTime();
        this.endTime = row.endTime();
        this.subjectId = row.subjectId();
        this.subjectName = row.subjectName();
        this.durationMinutes = row.durationMinutes();
        this.status = row.status().name();
        this.maxCapacity = row.maxCapacity();
        this.occupied = occupied;
        this.remainingSlots = row.maxCapacity() - occupied;
        this.horseNames = horseNames;
    }

    public Long getLotId() { return lotId; }
    public LocalDate getLotDate() { return lotDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public Long getSubjectId() { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getStatus() { return status; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public Integer getOccupied() { return occupied; }
    public Integer getRemainingSlots() { return remainingSlots; }
    public List<String> getHorseNames() { return horseNames; }
}
