package com.rtms.backend.training.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TrainerDashboardHorseResponse {
    private Long horseId;
    private String horseName;
    private String breed;
    private String stallCode;
    private Long planId;
    private String courseName;
    private String planStatus; // "ACTIVE", "UPCOMING", "COMPLETED", "NONE"
    private Integer completedSessions;
    private Integer totalSessions;
    private Double progressPercent;
    private Integer latestPerformanceRating;
    private Double avgPerformanceRating30d;
    private Integer alertsCount;
    private List<String> alertTitles = new ArrayList<>();
    private LocalDate startDate;
    private LocalDate endDate;
    private HorseSpecialtyResponse specialty;

    public TrainerDashboardHorseResponse() {
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public String getHorseName() {
        return horseName;
    }

    public void setHorseName(String horseName) {
        this.horseName = horseName;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getStallCode() {
        return stallCode;
    }

    public void setStallCode(String stallCode) {
        this.stallCode = stallCode;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getPlanStatus() {
        return planStatus;
    }

    public void setPlanStatus(String planStatus) {
        this.planStatus = planStatus;
    }

    public Integer getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(Integer completedSessions) {
        this.completedSessions = completedSessions;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Double progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getLatestPerformanceRating() {
        return latestPerformanceRating;
    }

    public void setLatestPerformanceRating(Integer latestPerformanceRating) {
        this.latestPerformanceRating = latestPerformanceRating;
    }

    public Double getAvgPerformanceRating30d() {
        return avgPerformanceRating30d;
    }

    public void setAvgPerformanceRating30d(Double avgPerformanceRating30d) {
        this.avgPerformanceRating30d = avgPerformanceRating30d;
    }

    public Integer getAlertsCount() {
        return alertsCount;
    }

    public void setAlertsCount(Integer alertsCount) {
        this.alertsCount = alertsCount;
    }

    public Integer getAlertCount() {
        return alertsCount;
    }

    public void setAlertCount(Integer alertCount) {
        this.alertsCount = alertCount;
    }

    public List<String> getAlertTitles() {
        return alertTitles;
    }

    public void setAlertTitles(List<String> alertTitles) {
        this.alertTitles = alertTitles;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public HorseSpecialtyResponse getSpecialty() {
        return specialty;
    }

    public void setSpecialty(HorseSpecialtyResponse specialty) {
        this.specialty = specialty;
    }
}
