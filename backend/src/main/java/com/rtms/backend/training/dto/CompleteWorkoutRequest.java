package com.rtms.backend.training.dto;

import java.math.BigDecimal;

/**
 * Trainer ghi nhận kết quả sau buổi tập.
 *
 * Theo đề tài, đây là việc của Head Trainer ("Đánh giá phong độ, ghi nhận chỉ
 * số buổi tập và đưa ra nhận xét chuyên môn sau mỗi buổi tập"), KHÔNG phải của
 * Groom. Groom chỉ tick hoàn thành các việc chăm sóc trong groom_daily_tasks.
 * (The trainer records the result after a workout. Per the project brief this is the head trainer job
 *  ("assess form, record workout metrics and give professional feedback after each workout"), NOT the groom job.
 *  The groom only ticks off care tasks in groom_daily_tasks.)
 */
public class CompleteWorkoutRequest {

    private BigDecimal actualDistanceMeters;
    private BigDecimal actualDurationMinutes;
    private BigDecimal topSpeedKmh;
    private BigDecimal averageSpeedKmh;
    private Integer averageHeartRate;
    private Integer maxHeartRate;
    private Integer recoveryHeartRate;
    private Integer performanceRating;   // 1..10
    private String trainerFeedback;
    private String videoUrl;

    public BigDecimal getActualDistanceMeters() { return actualDistanceMeters; }
    public void setActualDistanceMeters(BigDecimal v) { this.actualDistanceMeters = v; }

    public BigDecimal getActualDurationMinutes() { return actualDurationMinutes; }
    public void setActualDurationMinutes(BigDecimal v) { this.actualDurationMinutes = v; }

    public BigDecimal getTopSpeedKmh() { return topSpeedKmh; }
    public void setTopSpeedKmh(BigDecimal v) { this.topSpeedKmh = v; }

    public BigDecimal getAverageSpeedKmh() { return averageSpeedKmh; }
    public void setAverageSpeedKmh(BigDecimal v) { this.averageSpeedKmh = v; }

    public Integer getAverageHeartRate() { return averageHeartRate; }
    public void setAverageHeartRate(Integer v) { this.averageHeartRate = v; }

    public Integer getMaxHeartRate() { return maxHeartRate; }
    public void setMaxHeartRate(Integer v) { this.maxHeartRate = v; }

    public Integer getRecoveryHeartRate() { return recoveryHeartRate; }
    public void setRecoveryHeartRate(Integer v) { this.recoveryHeartRate = v; }

    public Integer getPerformanceRating() { return performanceRating; }
    public void setPerformanceRating(Integer v) { this.performanceRating = v; }

    public String getTrainerFeedback() { return trainerFeedback; }
    public void setTrainerFeedback(String v) { this.trainerFeedback = v; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String v) { this.videoUrl = v; }
}
