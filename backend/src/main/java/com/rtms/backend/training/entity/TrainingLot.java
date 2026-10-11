package com.rtms.backend.training.entity;

import com.rtms.backend.training.enums.LotStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Một LOT = một nhóm chiến mã cùng ra sân chạy CÙNG MỘT BÀI, cùng khung giờ,
 * dưới sự quan sát của một Trainer.
 * (A LOT = a group of horses going out to run the SAME SUBJECT in the same time slot, under one trainer.)
 *
 * Vì subject_id nằm ở đây (không nằm ở workout), quy tắc "ngựa chung lot phải
 * chung bài" trở thành ĐÚNG THEO CẤU TRÚC — không có cách nào biểu diễn
 * trạng thái sai, nên không cần validation.
 * (Because subject_id lives here (not on the workout), the rule "horses sharing a lot must share the subject" is TRUE BY STRUCTURE:
 *  a wrong state cannot even be represented, so no validation is needed.)
 */
@Entity
@Table(name = "training_lots")
public class TrainingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "lot_date", nullable = false)
    private LocalDate lotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotStatus status = LotStatus.SCHEDULED;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTrainerId() { return trainerId; }
    public void setTrainerId(Long trainerId) { this.trainerId = trainerId; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public LocalDate getLotDate() { return lotDate; }
    public void setLotDate(LocalDate lotDate) { this.lotDate = lotDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public LotStatus getStatus() { return status; }
    public void setStatus(LotStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
