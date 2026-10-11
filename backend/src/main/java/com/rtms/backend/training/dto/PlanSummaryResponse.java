package com.rtms.backend.training.dto;
import com.rtms.backend.training.entity.HorseTrainingPlan;

import com.rtms.backend.training.enums.TrainingPlanStatus;

import java.time.LocalDate;

/**
 * Một dòng trong danh sách kế hoạch huấn luyện.
 * (One row in the training plan list.)
 *
 * VÌ SAO CẦN DTO RIÊNG thay vì trả thẳng entity HorseTrainingPlan:
 * dự án không dùng quan hệ JPA trên entity nghiệp vụ, nên entity chỉ có
 * horseId / courseId dạng số. Trả nguyên entity thì màn hình danh sách hiện
 * "Kế hoạch #27 · ngựa #15 · khoá #3" — không ai đọc được. Tên phải được
 * ghép ở tầng service, giống cách PlanWorkoutItemResponse đã mang subjectName.
 *
 * Ba con số tiến độ đi kèm luôn để danh sách khỏi phải gọi thêm API cho
 * từng dòng.
 * (WHY A DEDICATED DTO instead of returning the HorseTrainingPlan entity: the project uses no JPA relations on business entities, so the
 *  entity only has horseId / courseId as numbers. Returning it would make the list show "Plan #27 · horse #15 · course #3", which nobody
 *  can read. Names must be joined in the query, like PlanWorkoutItemResponse already carries subjectName. The three progress numbers
 *  come along so the list needs no extra API call per row.)
 */
public class PlanSummaryResponse {

    private Long planId;
    private Long horseId;
    private String horseName;
    private Long courseId;
    private String courseName;
    private LocalDate startDate;
    private LocalDate endDate;
    private TrainingPlanStatus status;

    /** Tổng số buổi đã sinh (bằng course.totalSessions lúc tạo). */
    // (Total workouts generated (equal to course.totalSessions at creation).)
    private Integer totalSessions;
    private Integer completedSessions;

    /**
     * Buổi bị huỷ — bị TRỪ khỏi mẫu số khi tính tiến độ.
     * Khoá 12 buổi huỷ 1 sẽ đóng ở 11/11 (100%), không treo mãi ở 91%.
     * (Cancelled workouts are SUBTRACTED from the denominator when computing progress. A 12-session course with 1 cancelled closes at
     *  11/11 (100%) instead of hanging forever at 91%.)
     */
    private Integer cancelledSessions;

    public PlanSummaryResponse(Long planId, Long horseId, String horseName,
                               Long courseId, String courseName,
                               LocalDate startDate, LocalDate endDate,
                               TrainingPlanStatus status,
                               Long totalSessions, Long completedSessions,
                               Long cancelledSessions) {
        this.planId = planId;
        this.horseId = horseId;
        this.horseName = horseName;
        this.courseId = courseId;
        this.courseName = courseName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        // COUNT/SUM của SQL trả Long; tiến độ không bao giờ vượt Integer. (SQL COUNT/SUM return Long; progress never exceeds the Integer range)
        // (SQL COUNT/SUM return Long; progress never exceeds Integer range.)
        this.totalSessions = totalSessions.intValue();
        this.completedSessions = completedSessions.intValue();
        this.cancelledSessions = cancelledSessions.intValue();
    }

    public Long getPlanId() { return planId; }
    public Long getHorseId() { return horseId; }
    public String getHorseName() { return horseName; }
    public Long getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public TrainingPlanStatus getStatus() { return status; }
    public Integer getTotalSessions() { return totalSessions; }
    public Integer getCompletedSessions() { return completedSessions; }
    public Integer getCancelledSessions() { return cancelledSessions; }
}
