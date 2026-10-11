package com.rtms.backend.training.enums;

/**
 * Nhóm của bài tập (chỉ để phân loại và hiển thị, không có ràng buộc nghiệp vụ nên là enum chứ không phải bảng).
 * (The category of a subject. Used only for grouping and display with no business rule attached, so it is an
 * enum rather than a table.)
 */
public enum SubjectCategory {
    ENDURANCE,    // Thể lực & Sức bền (Fitness & endurance)
    SPEED,        // Tốc độ & Nước rút (Speed & sprint)
    GATE_START,   // Kỹ thuật lồng xuất phát (Starting-gate technique)
    RECOVERY      // Hồi phục & Thả lỏng (Recovery & cool-down)
}
