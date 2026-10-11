package com.rtms.backend.training.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.CreateHorseTrainingPlanRequest;
import com.rtms.backend.training.dto.HorseTrainingPlanDetailResponse;
import com.rtms.backend.training.dto.JoinableCohortResponse;
import com.rtms.backend.training.dto.PlanSummaryResponse;
import com.rtms.backend.training.dto.TrainerDashboardHorseResponse;
import com.rtms.backend.training.service.HorsePerformanceService;
import com.rtms.backend.training.service.HorseTrainingPlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/training-plans")
public class HorseTrainingPlanController {

    private final HorseTrainingPlanService planService;
    private final HorsePerformanceService performanceService;

    public HorseTrainingPlanController(HorseTrainingPlanService planService,
                                       HorsePerformanceService performanceService) {
        this.planService = planService;
        this.performanceService = performanceService;
    }

    /**
     * Dashboard tổng quan tiến độ và thể lực toàn khu của Trainer (FE-7.1).
     * (Overview dashboard of progress and fitness for the trainer whole area (FE-7.1).)
     */
    @PreAuthorize("hasAuthority('TRAINING_PLAN_VIEW')")
    @GetMapping("/dashboard")
    public ApiResponse<List<TrainerDashboardHorseResponse>> getDashboard(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(performanceService.getTrainerDashboard(currentUser));
    }

    /**
     * Danh sách kế hoạch kèm TÊN ngựa, TÊN khóa và tiến độ. Cần quyền TRAINING_PLAN_LIST riêng (chỉ Trainer và
     * Manager), để Owner và Thú y chỉ xem được từng kế hoạch theo id.
     * (Plan list with horse NAME, course NAME and progress. Needs the separate TRAINING_PLAN_LIST permission
     * (trainer and manager only), so owners and vets can only open a single plan by id.)
     */
    @PreAuthorize("hasAuthority('TRAINING_PLAN_LIST')")
    @GetMapping
    public ApiResponse<List<PlanSummaryResponse>> getAllPlans(
            @RequestParam(required = false) Long horseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(planService.getPlanSummaries(horseId, currentUser));
    }

    @PreAuthorize("hasAuthority('TRAINING_PLAN_VIEW')")
    @GetMapping("/{id}")
    public ApiResponse<HorseTrainingPlanDetailResponse> getPlanById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(planService.getPlanById(id, currentUser));
    }

    @PreAuthorize("hasAuthority('TRAINING_PLAN_CREATE')")
    @PostMapping
    public ApiResponse<List<HorseTrainingPlanDetailResponse>> createPlan(
            @RequestBody CreateHorseTrainingPlanRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(planService.createPlan(request, currentUser));
    }

    /**
     * Gợi ý nhóm để ghi danh chung (tiết kiệm khe giờ vàng); hệ thống chỉ gợi ý, Trainer tự quyết dựa trên
     * waitDays so với sharedSessions.
     * (Suggests cohorts to enroll together (saves golden-window slots); the system only suggests and the trainer
     * decides from waitDays versus sharedSessions.)
     */
    @PreAuthorize("hasAuthority('TRAINING_PLAN_VIEW')")
    @GetMapping("/joinable-cohorts")
    public ApiResponse<List<JoinableCohortResponse>> getJoinableCohorts(
            @RequestParam Long courseId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(planService.getJoinableCohorts(courseId, currentUser));
    }
}
