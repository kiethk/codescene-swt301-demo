package com.rtms.backend.training.enums;

/**
 * Mức độ sẵn sàng THI ĐẤU (không phải sẵn sàng huấn luyện).
 * Dùng chung cho đánh giá lúc nhập học và đánh giá định kỳ.
 * (Readiness to RACE (not readiness to train). Shared by the admission assessment and the periodic assessment.)
 */
public enum RacingReadinessStatus {

    /** Đủ điều kiện đăng ký giải đua ngay. (Eligible to register for a race right away.) */
    READY,

    /** Chưa đủ, nhưng cứ theo lộ trình tập là sẽ đạt. (Not yet, but will get there by following the training path.) */
    NEEDS_MORE_TRAINING,

    /**
     * Không phù hợp với đua — tập thêm cũng không giải quyết được
     * (dáng vóc lệch, tính nết không hợp, quá tuổi...).
     *
     * Đổi tên từ NOT_READY vì tên cũ không phân biệt được với
     * NEEDS_MORE_TRAINING. Đây là kênh DUY NHẤT để Trainer báo hiệu
     * "không nên nhận con này" — vì Trainer không có quyền reject đơn.
     * (Not suitable for racing: more training would not solve it (poor build, unsuitable temperament, too old...). Renamed from NOT_READY because the old name
     *  could not be told apart from NEEDS_MORE_TRAINING. This is the ONLY channel for the trainer to signal "do not accept this horse", because the trainer
     *  cannot reject an application.)
     */
    UNSUITABLE
}
