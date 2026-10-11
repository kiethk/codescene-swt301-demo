package com.rtms.backend.racing.dto;

import com.rtms.backend.racing.enums.RaceRegistrationStatus;
import com.rtms.backend.racing.entity.RaceRegistration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class RaceRegistrationResponse {

    private Long id;
    private Long horseId;
    private String horseName;
    private String horseRegistrationNumber;
    private Long trainerId;
    private String raceName;
    private String raceCategory;
    private String location;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String organizer;
    private String sourceUrl;
    private LocalDate nominationDeadline;
    private BigDecimal distanceMeters;
    private String trackType;
    private String prizeDetails;
    private String selectionReason;
    private String trainerNotes;
    private RaceRegistrationStatus status;
    private Long reviewedById;
    private String managerFeedback;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public RaceRegistrationResponse() {}

    public RaceRegistrationResponse(RaceRegistration reg, String horseName, String horseRegistrationNumber) {
        this.id = reg.getId();
        this.horseId = reg.getHorseId();
        this.horseName = horseName;
        this.horseRegistrationNumber = horseRegistrationNumber;
        this.trainerId = reg.getTrainerId();
        this.raceName = reg.getRaceName();
        this.raceCategory = reg.getRaceCategory();
        this.location = reg.getLocation();
        this.eventDate = reg.getEventDate();
        this.eventTime = reg.getEventTime();
        this.organizer = reg.getOrganizer();
        this.sourceUrl = reg.getSourceUrl();
        this.nominationDeadline = reg.getNominationDeadline();
        this.distanceMeters = reg.getDistanceMeters();
        this.trackType = reg.getTrackType();
        this.prizeDetails = reg.getPrizeDetails();
        this.selectionReason = reg.getSelectionReason();
        this.trainerNotes = reg.getTrainerNotes();
        this.status = reg.getStatus();
        this.reviewedById = reg.getReviewedById();
        this.managerFeedback = reg.getManagerFeedback();
        this.reviewedAt = reg.getReviewedAt();
        this.createdAt = reg.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getHorseRegistrationNumber() {
        return horseRegistrationNumber;
    }

    public void setHorseRegistrationNumber(String horseRegistrationNumber) {
        this.horseRegistrationNumber = horseRegistrationNumber;
    }

    public Long getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(Long trainerId) {
        this.trainerId = trainerId;
    }

    public String getRaceName() {
        return raceName;
    }

    public void setRaceName(String raceName) {
        this.raceName = raceName;
    }

    public String getRaceCategory() {
        return raceCategory;
    }

    public void setRaceCategory(String raceCategory) {
        this.raceCategory = raceCategory;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDate getNominationDeadline() {
        return nominationDeadline;
    }

    public void setNominationDeadline(LocalDate nominationDeadline) {
        this.nominationDeadline = nominationDeadline;
    }

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(BigDecimal distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public String getTrackType() {
        return trackType;
    }

    public void setTrackType(String trackType) {
        this.trackType = trackType;
    }

    public String getPrizeDetails() {
        return prizeDetails;
    }

    public void setPrizeDetails(String prizeDetails) {
        this.prizeDetails = prizeDetails;
    }

    public String getSelectionReason() {
        return selectionReason;
    }

    public void setSelectionReason(String selectionReason) {
        this.selectionReason = selectionReason;
    }

    public String getTrainerNotes() {
        return trainerNotes;
    }

    public void setTrainerNotes(String trainerNotes) {
        this.trainerNotes = trainerNotes;
    }

    public RaceRegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RaceRegistrationStatus status) {
        this.status = status;
    }

    public Long getReviewedById() {
        return reviewedById;
    }

    public void setReviewedById(Long reviewedById) {
        this.reviewedById = reviewedById;
    }

    public String getManagerFeedback() {
        return managerFeedback;
    }

    public void setManagerFeedback(String managerFeedback) {
        this.managerFeedback = managerFeedback;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
