package com.rtms.backend.training.enums;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.medical.entity.HealthRecord;

/**
 * Ngựa có được tập hay không — MỘT kiểu duy nhất dùng cho cả trạng thái hiện
 * hành của ngựa (Horse.trainingDecision) lẫn kết luận của từng lần khám
 * (HealthRecord.trainingDecision).
 *
 * Không có mức "tập hạn chế": thương nhẹ là BLOCKED kèm một lịch khám lại gần
 * (nextSchedule). Mức độ nặng nhẹ thể hiện ở thời hạn nghỉ, không ở số trạng thái.
 * (Whether the horse may train: ONE single type used for both the current state of the horse (Horse.trainingDecision) and the conclusion
 *  of each examination (HealthRecord.trainingDecision). There is no "limited training" level: a mild injury is BLOCKED with a near follow-up
 *  examination (nextSchedule). Severity shows in the length of the rest, not in the number of states.)
 */
public enum TrainingDecision {
    ALLOWED,
    BLOCKED
}
