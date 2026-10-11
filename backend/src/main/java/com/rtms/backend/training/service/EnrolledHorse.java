package com.rtms.backend.training.service;

import com.rtms.backend.horse.entity.Horse;

/**
 * Bản ghi tạm: chiến mã đã được kiểm tra khi ghi danh, kèm Groom đã phân giải.
 * (Temporary record: a horse validated at enrollment, with its resolved groom.)
 */
public record EnrolledHorse(Horse horse, Long groomId) {
}
