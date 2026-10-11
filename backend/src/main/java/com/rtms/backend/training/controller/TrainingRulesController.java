package com.rtms.backend.training.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.config.FarmSchedulePolicy;
import com.rtms.backend.training.dto.TrainingRulesResponse;
import com.rtms.backend.training.service.HorsePerformanceService;
import com.rtms.backend.training.service.SubjectService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Công bố các quy tắc huấn luyện cho giao diện. Chỉ đọc, không chứa dữ liệu nhạy cảm nên mọi người dùng đã
 * đăng nhập đều xem được.
 * (Publishes the training rules to the UI. Read-only and not sensitive, so every signed-in user may read it.)
 */
@RestController
@RequestMapping("/api/training-rules")
public class TrainingRulesController {

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ApiResponse<TrainingRulesResponse> getRules() {
        return ApiResponse.success(new TrainingRulesResponse(
                FarmSchedulePolicy.GOLDEN_HOURS_START,
                FarmSchedulePolicy.GOLDEN_HOURS_END,
                FarmSchedulePolicy.goldenWindowMinutes(),
                FarmSchedulePolicy.DEFAULT_LOT_CAPACITY,
                SubjectService.MIN_DURATION_MINUTES,
                SubjectService.MAX_DURATION_MINUTES,
                HorsePerformanceService.MAX_HEART_RATE_LIMIT,
                HorsePerformanceService.RECOVERY_HEART_RATE_LIMIT));
    }
}
