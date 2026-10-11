package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.TrainingDay;

import java.time.LocalDate;
import java.util.Set;

/**
 * Gợi ý nhóm để ghi danh chung, giúp tiết kiệm khe giờ vàng.
 * (A cohort suggestion to enroll together, saving golden-window slots.)
 *
 * Hệ thống chỉ GỢI Ý, không ép. Trainer nhìn waitDays (ngựa phải nằm không
 * bao lâu) đối chiếu sharedSessions (được chung bao nhiêu buổi) rồi tự quyết.
 * (The system only SUGGESTS, it never forces. The trainer compares waitDays (how long the horse must sit idle) with sharedSessions
 *  (how many sessions can be shared) and decides.)
 */
public class JoinableCohortResponse {

    private String cohortStatus;          // UPCOMING | ACTIVE
    private Long courseId;
    private String courseName;

    /**
     * Ngày nên cho con ngựa mới bắt đầu.
     *  - Nhóm UPCOMING: chính là startDate của nhóm -> đồng bộ TRỌN khoá.
     *  - Nhóm ACTIVE:   ngày gần nhất nhóm quay về bài đầu khoá (i % n == 0)
     *                   -> đồng bộ từ đó tới hết khoá của nhóm.
     * (The date the new horse should start. UPCOMING cohort: the cohort startDate -> in sync for the WHOLE course.
     *  ACTIVE cohort: the nearest date the cohort returns to the first subject (i % n == 0) -> in sync from then to the end of the cohort course.)
     */
    private LocalDate suggestedStartDate;

    private Set<TrainingDay> trainingDays;
    private Integer horseCount;

    /**
     * Số buổi ĐỒNG PHA — cùng ngày, cùng bài tập.
     *
     * KHÔNG phải "số buổi chung lot". Đồng pha là ĐIỀU KIỆN CẦN để chung lot,
     * không phải bảo đảm: nhóm vẫn bị tách sang lot thứ hai nếu vượt sức chứa
     * (BR-10), hoặc nếu hai con cùng một Groom (BR-09).
     * (Number of IN-PHASE sessions: same day, same subject. NOT "the number of sessions in the same lot". Being in phase is a NECESSARY
     *  condition for sharing a lot, not a guarantee: the group still splits into a second lot when it exceeds capacity (BR-10) or when
     *  two horses share one groom (BR-09).)
     */
    private Integer sharedSessions;

    private Integer totalSessions;
    private Long waitDays;                // số ngày phải chờ tính từ hôm nay (days to wait counted from today)

    /**
     * Sức chứa một lot, để giao diện tự cảnh báo khi nhóm sắp tràn.
     * Trả từ backend thay vì để frontend hardcode số 6.
     * (Capacity of one lot, so the UI can warn when a cohort is about to overflow. Returned by the backend instead of the
     *  frontend hardcoding the number 6.)
     */
    private Integer lotCapacity;

    private String note;

    public JoinableCohortResponse(String cohortStatus, Long courseId, String courseName,
                                  LocalDate suggestedStartDate, Set<TrainingDay> trainingDays,
                                  Integer horseCount, Integer sharedSessions,
                                  Integer totalSessions, Long waitDays,
                                  Integer lotCapacity, String note) {
        this.cohortStatus = cohortStatus;
        this.courseId = courseId;
        this.courseName = courseName;
        this.suggestedStartDate = suggestedStartDate;
        this.trainingDays = trainingDays;
        this.horseCount = horseCount;
        this.sharedSessions = sharedSessions;
        this.totalSessions = totalSessions;
        this.waitDays = waitDays;
        this.lotCapacity = lotCapacity;
        this.note = note;
    }

    public Integer getLotCapacity() { return lotCapacity; }
    public void setLotCapacity(Integer lotCapacity) { this.lotCapacity = lotCapacity; }

    public String getCohortStatus() { return cohortStatus; }
    public Long getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public LocalDate getSuggestedStartDate() { return suggestedStartDate; }
    public Set<TrainingDay> getTrainingDays() { return trainingDays; }
    public Integer getHorseCount() { return horseCount; }
    public Integer getSharedSessions() { return sharedSessions; }
    public Integer getTotalSessions() { return totalSessions; }
    public Long getWaitDays() { return waitDays; }
    public String getNote() { return note; }
}
