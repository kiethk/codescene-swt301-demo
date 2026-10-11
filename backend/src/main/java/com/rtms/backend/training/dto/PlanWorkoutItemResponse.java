package com.rtms.backend.training.dto;

import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.entity.TrainingWorkout;
import com.rtms.backend.training.enums.WorkoutStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Workout kèm thông tin lot — vì workout không còn tự giữ ngày/giờ/bài tập.
 * (A workout with its lot info, since a workout no longer holds the date, time or subject itself.)
 */
public class PlanWorkoutItemResponse {

    private Long workoutId;
    private Long lotId;
    private LocalDate lotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long subjectId;
    private String subjectName;
    private Long horseId;
    private Long assignedGroomId;
    private String status;

    private BigDecimal actualDistanceMeters;
    private Integer averageHeartRate;
    private Integer maxHeartRate;
    private BigDecimal topSpeedKmh;
    private Integer performanceRating;
    private String trainerFeedback;

    /**
     * Tổng số chiến mã còn hiệu lực trong lot này.
     *
     * Vì sao cần: trang chi tiết kế hoạch chỉ hiển thị MỘT con ngựa, nên
     * lotId trông như một con số vô nghĩa. Biết lot có mấy con thì Trainer
     * mới thấy cơ chế ghép nhóm đang hoạt động — và hiểu rằng assignedGroomId
     * là người dắt RIÊNG con này, không phải "groom của lot".
     *
     * Được tính bằng sub-query ngay trong câu SELECT của TrainingWorkoutRepository.findPlanWorkoutItems,
     * không phải mỗi buổi một truy vấn.
     * (Total number of ACTIVE horses in this lot. Why it is needed: the plan detail page shows only ONE horse, so lotId looks like a meaningless
     *  number. Knowing how many horses share the lot shows the trainer that grouping is working, and that assignedGroomId is the person leading
     *  THIS horse alone, not "the lot groom".)
     * (Computed by a sub-query inside TrainingWorkoutRepository.findPlanWorkoutItems, not one query per workout.)
     */
    private Integer lotOccupancy;

    public PlanWorkoutItemResponse(TrainingWorkout w, TrainingLot lot, String subjectName) {
        this.workoutId = w.getId();
        this.lotId = lot.getId();
        this.lotDate = lot.getLotDate();
        this.startTime = lot.getStartTime();
        this.endTime = lot.getEndTime();
        this.subjectId = lot.getSubjectId();
        this.subjectName = subjectName;
        this.horseId = w.getHorseId();
        this.assignedGroomId = w.getAssignedToId();
        this.status = w.getStatus().name();
        this.actualDistanceMeters = w.getActualDistanceMeters();
        this.averageHeartRate = w.getAverageHeartRate();
        this.maxHeartRate = w.getMaxHeartRate();
        this.topSpeedKmh = w.getTopSpeedKmh();
        this.performanceRating = w.getPerformanceRating();
        this.trainerFeedback = w.getTrainerFeedback();
    }

    /**
     * Constructor cho truy vấn JPQL (SELECT new ...): mọi cột đã được JOIN sẵn,
     * kể cả số ngựa trong lot.
     * (Constructor for the JPQL query (SELECT new ...): every column is already joined, including the horse count in the lot.)
     * (Constructor for the JPQL projection: every column is already joined, including the lot's horse count.)
     */
    public PlanWorkoutItemResponse(Long workoutId, Long lotId, LocalDate lotDate,
                                   LocalTime startTime, LocalTime endTime,
                                   Long subjectId, String subjectName,
                                   Long horseId, Long assignedGroomId, WorkoutStatus status,
                                   BigDecimal actualDistanceMeters, Integer averageHeartRate,
                                   Integer maxHeartRate, BigDecimal topSpeedKmh,
                                   Integer performanceRating, String trainerFeedback,
                                   Long lotOccupancy) {
        this.workoutId = workoutId;
        this.lotId = lotId;
        this.lotDate = lotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.horseId = horseId;
        this.assignedGroomId = assignedGroomId;
        this.status = status.name();
        this.actualDistanceMeters = actualDistanceMeters;
        this.averageHeartRate = averageHeartRate;
        this.maxHeartRate = maxHeartRate;
        this.topSpeedKmh = topSpeedKmh;
        this.performanceRating = performanceRating;
        this.trainerFeedback = trainerFeedback;
        this.lotOccupancy = lotOccupancy == null ? null : lotOccupancy.intValue();
    }

    public Integer getLotOccupancy() { return lotOccupancy; }
    public void setLotOccupancy(Integer lotOccupancy) { this.lotOccupancy = lotOccupancy; }

    public Long getWorkoutId() { return workoutId; }
    public Long getLotId() { return lotId; }
    public LocalDate getLotDate() { return lotDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public Long getSubjectId() { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public Long getHorseId() { return horseId; }
    public Long getAssignedGroomId() { return assignedGroomId; }
    public String getStatus() { return status; }
    public BigDecimal getActualDistanceMeters() { return actualDistanceMeters; }
    public Integer getAverageHeartRate() { return averageHeartRate; }
    public Integer getMaxHeartRate() { return maxHeartRate; }
    public BigDecimal getTopSpeedKmh() { return topSpeedKmh; }
    public Integer getPerformanceRating() { return performanceRating; }
    public String getTrainerFeedback() { return trainerFeedback; }
}
