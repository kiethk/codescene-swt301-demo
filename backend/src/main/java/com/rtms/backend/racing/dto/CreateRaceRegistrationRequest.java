package com.rtms.backend.racing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class CreateRaceRegistrationRequest {

    @NotNull(message = "Horse must not be empty")
    private Long horseId;

    @NotBlank(message = "Race name must not be empty")
    @Size(max = 255, message = "Race name must be at most 255 characters")
    private String raceName;

    @NotBlank(message = "Race category must not be empty")
    @Size(max = 255, message = "Race category must be at most 255 characters")
    private String raceCategory;

    @NotBlank(message = "Venue must not be empty")
    @Size(max = 255, message = "Venue must be at most 255 characters")
    private String location;

    @NotNull(message = "Race date must not be empty")
    private LocalDate eventDate;

    private LocalTime eventTime;

    @Size(max = 255, message = "Organizer must be at most 255 characters")
    private String organizer;

    @Size(max = 1000, message = "Source link must be at most 1000 characters")
    private String sourceUrl;

    private LocalDate nominationDeadline;

    @Positive(message = "Distance must be a positive number")
    private BigDecimal distanceMeters;

    @Size(max = 50, message = "Surface must be at most 50 characters")
    private String trackType;

    private String prizeDetails;

    @NotBlank(message = "Nomination reason must not be empty")
    private String selectionReason;

    private String trainerNotes;

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
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
}
