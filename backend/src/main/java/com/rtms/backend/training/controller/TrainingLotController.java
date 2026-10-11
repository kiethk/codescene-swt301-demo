package com.rtms.backend.training.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.RescheduleLotRequest;
import com.rtms.backend.training.dto.TrainingLotResponse;
import com.rtms.backend.training.service.TrainingLotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller chỉ nhận request và gọi service; mọi logic phân quyền theo vai trò nằm ở TrainingLotService.
 * (The controller only receives requests and calls the service; all role-based logic lives in TrainingLotService.)
 */
@RestController
@RequestMapping("/api/lots")
public class TrainingLotController {

    private final TrainingLotService lotService;

    public TrainingLotController(TrainingLotService lotService) {
        this.lotService = lotService;
    }

    /**
     * Xem lịch lot theo khoảng ngày; "lịch của tôi" có nghĩa khác nhau theo vai trò (xem TrainingLotService.getLots).
     * (Views the lot schedule over a date range; "my schedule" means different things per role, see
     * TrainingLotService.getLots.)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TRAINING_LOT_VIEW')")
    public ApiResponse<List<TrainingLotResponse>> getLots(
            @RequestParam(required = false) Long trainerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean includeCancelled,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(lotService.getLots(currentUser, trainerId, from, to, includeCancelled));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRAINING_LOT_VIEW')")
    public ApiResponse<TrainingLotResponse> getLotById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(lotService.getLot(id, currentUser));
    }

    /** Dời giờ lot: cả hàng ngựa trong lot dời theo. (Reschedules a lot: all horses in it move too.) */
    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('TRAINING_LOT_UPDATE')")
    public ApiResponse<TrainingLotResponse> reschedule(
            @PathVariable Long id,
            @RequestBody RescheduleLotRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(
                lotService.rescheduleLot(id, request.getNewStartTime(), currentUser.getUserId()));
    }

    /** Hủy lot và mọi buổi tập SCHEDULED bên trong. (Cancels a lot and every SCHEDULED workout inside.) */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TRAINING_LOT_UPDATE')")
    public ApiResponse<TrainingLotResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(lotService.cancelLot(id, currentUser.getUserId()));
    }
}
