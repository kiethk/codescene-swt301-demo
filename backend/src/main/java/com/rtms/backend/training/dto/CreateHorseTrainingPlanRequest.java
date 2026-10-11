package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.TrainingDay;

import java.time.LocalDate;
import java.util.List;

/**
 * GHI DANH THEO NHÓM.
 * (GROUP ENROLLMENT.)
 *
 * Ba trường quyết định sự đồng bộ (courseId, startDate, trainingDays) nằm
 * NGOÀI danh sách ngựa -> mọi con trong một lời gọi BẮT BUỘC đồng bộ.
 * Đây là ràng buộc ở cấp cấu trúc request, không phải validation có thể quên.
 * (The three fields that decide synchronization (courseId, startDate, trainingDays) live OUTSIDE the horse list, so every
 *  horse in one call MUST be synchronized. This is a constraint of the request structure, not a validation that can be forgotten.)
 *
 * Vì sao quan trọng: hai con ngựa chỉ chung lot suốt cả khoá khi thoả CẢ BA
 * điều kiện. Lệch một buổi hay lệch một thứ trong tuần là lệch pha VĨNH VIỄN,
 * vì bài tập chọn theo courseSubjects[i % n] và hiệu i_A - i_B không đổi.
 * (Why it matters: two horses share a lot for the whole course only when ALL THREE conditions hold. One session or one weekday
 *  out of step means being out of phase FOREVER, because subjects are chosen by courseSubjects[i % n] and i_A - i_B never changes.)
 *
 * ĐÃ BỎ so với bản cũ:
 *   - horseId (1 con)      -> thay bằng horses (N con)
 *   - groomId (cấp request) -> chuyển vào từng phần tử, và cho phép null
 *   - startTime / endTime   -> giờ giấc giờ do lot quyết định (BR-04 đã bỏ)
 * (REMOVED compared with the old version: horseId (1 horse) is replaced by horses (N horses); groomId (request level) moves into
 *  each element and may be null; startTime / endTime are now decided by the lot (BR-04 was dropped).)
 */
public class CreateHorseTrainingPlanRequest {

    /** Danh sách chiến mã ghi danh cùng lúc. Tối thiểu 1 con. (List of horses enrolled together. At least 1 horse.) */
    private List<HorseEnrollmentRequest> horses;

    private Long courseId;

    private LocalDate startDate;

    /**
     * Các thứ trong tuần muốn tập, ví dụ ["MONDAY","WEDNESDAY","FRIDAY"].
     * BẮT BUỘC — không còn mặc định ngầm T2-4-6 như bản cũ, vì giá trị này
     * giờ được LƯU XUỐNG DB và trở thành dữ liệu thật của hồ sơ.
     * (The weekdays to train on, for example ["MONDAY","WEDNESDAY","FRIDAY"]. REQUIRED: there is no silent Mon-Wed-Fri default any more,
     *  because this value is now STORED in the DB and becomes real record data.)
     */
    private List<TrainingDay> trainingDays;

    private String notes;

    public List<HorseEnrollmentRequest> getHorses() { return horses; }
    public void setHorses(List<HorseEnrollmentRequest> horses) { this.horses = horses; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public List<TrainingDay> getTrainingDays() { return trainingDays; }
    public void setTrainingDays(List<TrainingDay> trainingDays) { this.trainingDays = trainingDays; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}