package com.rtms.backend.racing.enums;

/**
 * Trạng thái của một đề cử đua: chờ quản lý duyệt, được duyệt, hoặc bị từ chối. Đã duyệt rồi thì không đổi nữa.
 * (The status of a race nomination: waiting for the manager, approved, or rejected. Once reviewed it never changes.)
 */
public enum RaceRegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
