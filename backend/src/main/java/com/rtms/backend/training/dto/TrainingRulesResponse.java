package com.rtms.backend.training.dto;

import java.time.LocalTime;

/**
 * Các quy tắc huấn luyện mà giao diện cần để hiển thị đúng (biểu đồ, lịch, biểu mẫu), lấy từ backend để UI
 * không tự hard-code số liệu nghiệp vụ.
 * (The training rules the UI needs to draw itself correctly (charts, schedule, forms), served by the backend so
 * the UI never hard-codes business numbers.)
 *
 * @param goldenHoursStart          đầu khung giờ vàng huấn luyện (start of the training golden hours)
 * @param goldenHoursEnd            cuối khung giờ vàng (end of the golden hours)
 * @param goldenWindowMinutes       tổng số phút của khung giờ vàng (total minutes of the golden hours)
 * @param lotCapacity               sức chứa mặc định của một lot (default capacity of a lot)
 * @param minSubjectDurationMinutes thời lượng tối thiểu của một bài tập (shortest allowed subject duration)
 * @param maxSubjectDurationMinutes thời lượng tối đa của một bài tập (longest allowed subject duration)
 * @param maxHeartRateLimit         nhịp tim tối đa an toàn, vượt thì cảnh báo (safe maximum heart rate)
 * @param recoveryHeartRateLimit    nhịp tim hồi phục an toàn, vượt thì cảnh báo (safe recovery heart rate)
 */
public record TrainingRulesResponse(LocalTime goldenHoursStart,
                                    LocalTime goldenHoursEnd,
                                    int goldenWindowMinutes,
                                    int lotCapacity,
                                    int minSubjectDurationMinutes,
                                    int maxSubjectDurationMinutes,
                                    int maxHeartRateLimit,
                                    int recoveryHeartRateLimit) {
}
