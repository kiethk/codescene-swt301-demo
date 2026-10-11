package com.rtms.backend.training.enums;

public enum LotStatus {
    SCHEDULED,     // đã xếp lịch, chưa diễn ra (scheduled, not held yet)
    COMPLETED,     // đã hoàn thành (completed)
    CANCELLED      // đã huỷ — KHÔNG còn chiếm khe giờ, thuật toán xếp khe bỏ qua (cancelled - NO longer occupies a slot, the slot algorithm ignores it)
}
