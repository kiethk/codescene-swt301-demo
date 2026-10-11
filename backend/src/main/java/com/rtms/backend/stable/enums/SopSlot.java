package com.rtms.backend.stable.enums;
import com.rtms.backend.config.FarmSchedulePolicy;

import java.time.LocalTime;

/**
 * 5 việc thường nhật chuẩn (SOP) của Groom cho mỗi chiến mã.
 *
 * Vì sao là ENUM chứ không phải hằng số rời:
 *   - Đây là một TẬP ĐÓNG, CÓ THỨ TỰ, và cần DUYỆT QUA trong generator.
 *   - Thêm việc SOP thứ 6 = thêm một dòng enum, không đụng service.
 *   - Unit test duyệt SopSlot.values() nên KHÔNG vỡ khi đổi giờ.
 *     (Bản cũ hardcode cả 5 mốc trong test -> đổi khung giờ là 3 assertion vỡ.)
 *
 * Khung giờ vàng huấn luyện 06:00–10:00 khai báo ở config/FarmSchedulePolicy.java.
 * Không mốc nào dưới đây rơi vào khung đó — xem PLAN_02 mục 0.3.
 */
public enum SopSlot {

    /** 05:00 — ăn sáng. Cách lot đầu (06:00) đúng 60 phút, đủ tiêu hoá trước khi vận động. */
    MORNING_FEED(
            LocalTime.of(5, 0),
            GroomTaskType.FEEDING,
            "Morning feed, following the approved ration"),

    /** 05:30 — dọn chuồng đợt 1, trước khi dắt ngựa ra sân. */
    MUCK_OUT_MORNING(
            LocalTime.of(5, 30),
            GroomTaskType.MUCKING_OUT,
            "First muck-out: remove manure and replace bedding before training"),

    /** 10:00 — ngay sau khi lot cuối kết thúc. Gom việc hạ nhiệt của cả 3 con làm một mạch. */
    POST_WORKOUT_GROOMING(
            LocalTime.of(10, 0),
            GroomTaskType.GROOMING,
            "Full wash, grooming and hoof oil after training (10:00 - 11:30)"),

    /** 11:30 — ăn trưa. */
    LUNCH_FEED(
            LocalTime.of(11, 30),
            GroomTaskType.FEEDING,
            "Midday feed; check and refill the water trough"),

    /** 16:30 — ăn chiều + dọn chuồng đợt 2. */
    EVENING_FEED(
            LocalTime.of(16, 30),
            GroomTaskType.FEEDING,
            "Evening feed, top up hay, and second muck-out");

    private final LocalTime time;
    private final GroomTaskType taskType;
    private final String note;

    SopSlot(LocalTime time, GroomTaskType taskType, String note) {
        this.time = time;
        this.taskType = taskType;
        this.note = note;
    }

    public LocalTime getTime() { return time; }
    public GroomTaskType getTaskType() { return taskType; }
    public String getNote() { return note; }
}
