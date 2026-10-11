package com.rtms.backend.racing.entity;

import com.rtms.backend.racing.enums.RaceRegistrationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "race_registrations")
public class RaceRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "race_name", nullable = false)
    private String raceName;

    @Column(name = "race_category")
    private String raceCategory;

    private String location;

    @Column(name = "organizer")
    private String organizer;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "nomination_deadline")
    private LocalDate nominationDeadline;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time")
    private LocalTime eventTime;

    @Column(name = "distance_meters")
    private BigDecimal distanceMeters;

    @Column(name = "track_type")
    private String trackType;

    @Column(name = "prize_details", columnDefinition = "TEXT")
    private String prizeDetails;

    @Column(name = "selection_reason", columnDefinition = "TEXT")
    private String selectionReason;

    @Column(name = "jockey_name")
    private String jockeyName;

    @Column(name = "entry_fee")
    private BigDecimal entryFee;

    @Column(name = "trainer_notes")
    private String trainerNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RaceRegistrationStatus status = RaceRegistrationStatus.PENDING;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "manager_feedback")
    private String managerFeedback;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;


    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = RaceRegistrationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public String getJockeyName() {
        return jockeyName;
    }

    public void setJockeyName(String jockeyName) {
        this.jockeyName = jockeyName;
    }

    public BigDecimal getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(BigDecimal entryFee) {
        this.entryFee = entryFee;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
