package com.rtms.backend.training.entity;

import com.rtms.backend.config.TrainingDaySetConverter;
import com.rtms.backend.training.enums.TrainingDay;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "horse_training_plans")
public class HorseTrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // ===== THÊM MỚI (V43) — KHUÔN MẪU CỦA KHOÁ =====
    // (NEW (V43) - THE COURSE TEMPLATE)

    /**
     * Các thứ trong tuần khoá này tập.
     *
     * Vì sao phải lưu: khi một lot bị huỷ (ngựa chấn thương, trời mưa...),
     * không thể suy ngược "khoá này tập thứ mấy" từ các workout còn lại —
     * bạn không phân biệt được "khoá không tập thứ Sáu" với "có tập nhưng
     * buổi đó bị huỷ".
     * (The weekdays this course trains on. Why it must be stored: when a lot is cancelled (injury, rain...) you cannot work backwards
     *  from the remaining workouts to "which weekdays does this course train on" - you could not tell "the course does not train on
     *  Friday" from "it does but that workout was cancelled".)
     */
    @Convert(converter = TrainingDaySetConverter.class)
    @Column(name = "training_days", length = 100)
    private Set<TrainingDay> trainingDays = new LinkedHashSet<>();

    /**
     * Groom mặc định của khoá (ảnh chụp lúc tạo plan).
     * Một buổi lẻ có thể nhờ Groom khác dắt hộ — lúc đó workout.assignedToId
     * khác giá trị này, và ta vẫn biết ai là người phụ trách chính.
     * (The default groom of the course (a snapshot taken when the plan was created). A single workout may ask another groom to lead
     *  the horse; then workout.assignedToId differs from this value and we still know who is the main person in charge.)
     */
    @Column(name = "groom_id")
    private Long groomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingPlanStatus status = TrainingPlanStatus.ACTIVE;

    private String notes;

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

    public Long getHorseId() { return horseId; }
    public void setHorseId(Long horseId) { this.horseId = horseId; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Long getTrainerId() { return trainerId; }
    public void setTrainerId(Long trainerId) { this.trainerId = trainerId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Set<TrainingDay> getTrainingDays() { return trainingDays; }
    public void setTrainingDays(Set<TrainingDay> trainingDays) { this.trainingDays = trainingDays; }

    public Long getGroomId() { return groomId; }
    public void setGroomId(Long groomId) { this.groomId = groomId; }

    public TrainingPlanStatus getStatus() { return status; }
    public void setStatus(TrainingPlanStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}