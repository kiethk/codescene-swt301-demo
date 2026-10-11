package com.rtms.backend.training.service;

import com.rtms.backend.notification.dto.HorseAlertResponse;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.training.dto.DashboardHorseRow;
import com.rtms.backend.training.dto.DashboardPlanRow;
import com.rtms.backend.training.dto.HorseFitnessTrendItemResponse;
import com.rtms.backend.training.dto.HorseSpecialtyResponse;
import com.rtms.backend.training.dto.IncidentCountRow;
import com.rtms.backend.training.dto.RecentWorkoutRow;
import com.rtms.backend.training.dto.TrainerDashboardHorseResponse;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import com.rtms.backend.training.repository.HorseTrainingPlanRepository;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Theo dõi phong độ và thể lực chiến mã: biểu đồ thể lực, cảnh báo nguy cơ, và bảng tiến độ của Trainer.
 * (Tracks horse performance and fitness: the fitness chart, risk alerts, and the trainer progress board.)
 *
 * Mọi dữ liệu được tính khi đọc từ các buổi tập đã hoàn thành, không lưu bảng riêng.
 * (Everything is computed on read from completed workouts; nothing is stored in a table of its own.)
 */
@Service
public class HorsePerformanceService {

    private static final int RECENT_WORKOUT_DAYS = 30;
    private static final int INCIDENT_WINDOW_DAYS = 14;
    public static final int MAX_HEART_RATE_LIMIT = 220;
    public static final int RECOVERY_HEART_RATE_LIMIT = 100;
    private static final int REPEATED_INCIDENT_COUNT = 2;
    private static final LocalTime DEFAULT_END_TIME = LocalTime.of(10, 0);

    private final TrainingWorkoutRepository workoutRepository;
    private final HorseTrainingPlanRepository planRepository;
    private final GroomIncidentReportRepository incidentReportRepository;
    private final HorseViewAccessService accessService;
    private final HorseSpecialtyService specialtyService;

    public HorsePerformanceService(TrainingWorkoutRepository workoutRepository,
                                   HorseTrainingPlanRepository planRepository,
                                   GroomIncidentReportRepository incidentReportRepository,
                                   HorseViewAccessService accessService,
                                   HorseSpecialtyService specialtyService) {
        this.workoutRepository = workoutRepository;
        this.planRepository = planRepository;
        this.incidentReportRepository = incidentReportRepository;
        this.accessService = accessService;
        this.specialtyService = specialtyService;
    }

    // =================================================================
    // BIỂU ĐỒ THỂ LỰC (Fitness chart)
    // =================================================================

    /**
     * Chuỗi thời gian các buổi tập đã hoàn thành của một chiến mã (FE-7.2).
     * (Time series of a horse completed workouts.)
     */
    @Transactional(readOnly = true)
    public List<HorseFitnessTrendItemResponse> getFitnessTrend(Long horseId, LocalDate fromDate,
                                                               LocalDate toDate, AuthenticatedUser currentUser) {
        accessService.assertCanViewHorse(horseId, currentUser);

        // Bỏ trống thì dùng mốc bao trùm, để câu truy vấn luôn có BETWEEN cụ thể và khỏi phải viết
        // "(:from IS NULL OR ...)" vốn hay lỗi ép kiểu tham số null trên PostgreSQL.
        // (When empty, wide bounds are used so the query always has a concrete BETWEEN, avoiding
        // "(:from IS NULL OR ...)" which often breaks null parameter typing on PostgreSQL.)
        LocalDate from = fromDate != null ? fromDate : LocalDate.of(1900, 1, 1);
        LocalDate to = toDate != null ? toDate : LocalDate.of(2999, 12, 31);
        return workoutRepository.findFitnessTrend(horseId, from, to);
    }

    // =================================================================
    // CẢNH BÁO (Alerts)
    // =================================================================

    /**
     * Bộ luật cảnh báo nguy cơ chấn thương và suy giảm thể lực, tính động theo dữ liệu thời gian thực.
     * (The rule set for injury risk and fitness decline, computed dynamically from live data.)
     */
    @Transactional(readOnly = true)
    public List<HorseAlertResponse> getHorseAlerts(Long horseId, AuthenticatedUser currentUser) {
        accessService.assertCanViewHorse(horseId, currentUser);

        List<RecentWorkoutRow> recent = workoutRepository.findRecentCompletedWorkouts(
                List.of(horseId), LocalDate.now().minusDays(RECENT_WORKOUT_DAYS));
        long incidents = incidentReportRepository.countByHorseIdAndReportedAtAfter(horseId, incidentWindowStart());
        return evaluateAlerts(recent, incidents);
    }

    /**
     * Áp dụng các luật lên các buổi tập gần đây (mới nhất trước) và số sự cố. Dùng chung cho cả
     * getHorseAlerts và dashboard.
     * (Applies the rules to recent workouts (newest first) and the incident count. Shared by
     * getHorseAlerts and the dashboard.)
     *
     * Luật "khối lượng tăng đột biến" đã bị bỏ: khóa XOAY VÒNG bài tập có cự ly rất khác nhau nên tổng cự ly
     * tuần dao động theo lịch xoay chứ không theo tải thật, gây báo động giả.
     * (The "sudden volume spike" rule was dropped: the course ROTATES subjects of very different distance, so
     * weekly distance varies with the rotation rather than the real load, causing false alarms.)
     */
    private List<HorseAlertResponse> evaluateAlerts(List<RecentWorkoutRow> recent, long incidentCount) {
        List<HorseAlertResponse> alerts = new ArrayList<>();
        maxHeartRateAlert(recent).ifPresent(alerts::add);
        recoveryHeartRateAlert(recent).ifPresent(alerts::add);
        performanceDropAlert(recent).ifPresent(alerts::add);
        repeatedIncidentsAlert(incidentCount).ifPresent(alerts::add);
        return alerts;
    }

    /** Luật 1: nhịp tim tối đa vượt ngưỡng; chỉ báo mốc gần nhất. (Rule 1: max heart rate over the limit; newest only.) */
    private Optional<HorseAlertResponse> maxHeartRateAlert(List<RecentWorkoutRow> recent) {
        return recent.stream()
                .filter(r -> r.maxHeartRate() != null && r.maxHeartRate() > MAX_HEART_RATE_LIMIT)
                .findFirst()
                .map(r -> new HorseAlertResponse(
                        "MAX_HEART_RATE_EXCEEDED",
                        "Maximum heart rate above safe limit",
                        String.format("Recorded a maximum heart rate of %d bpm (safe limit <= %d bpm) on %s.",
                                r.maxHeartRate(), MAX_HEART_RATE_LIMIT, r.lotDate()),
                        "DANGER",
                        triggeredAt(r),
                        r.maxHeartRate().doubleValue(),
                        (double) MAX_HEART_RATE_LIMIT));
    }

    /** Luật 2: hồi phục tim kém. (Rule 2: poor heart-rate recovery.) */
    private Optional<HorseAlertResponse> recoveryHeartRateAlert(List<RecentWorkoutRow> recent) {
        return recent.stream()
                .filter(r -> r.recoveryHeartRate() != null && r.recoveryHeartRate() > RECOVERY_HEART_RATE_LIMIT)
                .findFirst()
                .map(r -> new HorseAlertResponse(
                        "POOR_RECOVERY_HEART_RATE",
                        "Poor heart-rate recovery",
                        String.format("Post-workout recovery heart rate is abnormally high (%d bpm, safe limit <= %d bpm) on %s.",
                                r.recoveryHeartRate(), RECOVERY_HEART_RATE_LIMIT, r.lotDate()),
                        "DANGER",
                        triggeredAt(r),
                        r.recoveryHeartRate().doubleValue(),
                        (double) RECOVERY_HEART_RATE_LIMIT));
    }

    /** Luật 3: điểm phong độ giảm liên tiếp qua 3 buổi. (Rule 3: performance rating falling over 3 workouts.) */
    private Optional<HorseAlertResponse> performanceDropAlert(List<RecentWorkoutRow> recent) {
        List<Integer> ratings = recent.stream()
                .map(RecentWorkoutRow::performanceRating)
                .filter(rating -> rating != null)
                .limit(3)
                .toList();
        if (ratings.size() < 3) {
            return Optional.empty();
        }

        int newest = ratings.get(0);
        int middle = ratings.get(1);
        int oldest = ratings.get(2);
        if (newest >= middle || middle >= oldest) {
            return Optional.empty();
        }
        return Optional.of(new HorseAlertResponse(
                "CONSECUTIVE_PERFORMANCE_DROP",
                "Declining performance",
                String.format("Performance rating fell across the last 3 workouts (%d → %d → %d).",
                        oldest, middle, newest),
                "WARNING",
                LocalDateTime.now(),
                (double) newest,
                (double) oldest));
    }

    /** Luật 4: sự cố lặp lại trong 14 ngày. (Rule 4: repeated incidents within 14 days.) */
    private Optional<HorseAlertResponse> repeatedIncidentsAlert(long incidentCount) {
        if (incidentCount < REPEATED_INCIDENT_COUNT) {
            return Optional.empty();
        }
        return Optional.of(new HorseAlertResponse(
                "REPEATED_INCIDENTS",
                "Repeated health incidents",
                String.format("The horse had %d incidents reported by grooms in the last %d days. A thorough veterinary check is needed.",
                        incidentCount, INCIDENT_WINDOW_DAYS),
                "DANGER",
                LocalDateTime.now(),
                (double) incidentCount,
                (double) REPEATED_INCIDENT_COUNT));
    }

    private LocalDateTime triggeredAt(RecentWorkoutRow row) {
        return row.lotDate().atTime(row.lotEndTime() != null ? row.lotEndTime() : DEFAULT_END_TIME);
    }

    private LocalDateTime incidentWindowStart() {
        return LocalDate.now().minusDays(INCIDENT_WINDOW_DAYS).atStartOfDay();
    }

    // =================================================================
    // DASHBOARD CỦA TRAINER (Trainer dashboard)
    // =================================================================

    /**
     * Bảng tiến độ và thể lực toàn khu của Trainer (FE-7.1).
     * (Progress and fitness board for the whole area of a trainer.)
     *
     * Số truy vấn là HẰNG SỐ (6), không phụ thuộc số ngựa: ngựa trong khu, kế hoạch, buổi tập 30 ngày,
     * số sự cố, buổi tập tính sở trường, đều lấy một lượt rồi ghép theo horseId.
     * (The number of queries is CONSTANT (6), independent of the horse count: area horses, plans, 30-day
     * workouts, incident counts and the specialty workouts are each fetched once and joined by horseId.)
     */
    @Transactional(readOnly = true)
    public List<TrainerDashboardHorseResponse> getTrainerDashboard(AuthenticatedUser currentUser) {
        Long trainerId = currentUser.getUserId();

        List<DashboardHorseRow> horses = planRepository.findDashboardHorses(trainerId);
        if (horses.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> horseIds = horses.stream().map(DashboardHorseRow::horseId).toList();

        Map<Long, List<DashboardPlanRow>> plansByHorse = planRepository.findDashboardPlans(trainerId).stream()
                .collect(Collectors.groupingBy(DashboardPlanRow::horseId));
        Map<Long, List<RecentWorkoutRow>> recentByHorse = workoutRepository
                .findRecentCompletedWorkouts(horseIds, LocalDate.now().minusDays(RECENT_WORKOUT_DAYS)).stream()
                .collect(Collectors.groupingBy(RecentWorkoutRow::horseId));
        Map<Long, Long> incidentsByHorse = incidentReportRepository
                .countIncidentsByHorseIds(horseIds, incidentWindowStart()).stream()
                .collect(Collectors.toMap(IncidentCountRow::horseId, IncidentCountRow::count));
        Map<Long, HorseSpecialtyResponse> specialtyByHorse = specialtyService.getSpecialties(horseIds);

        return horses.stream()
                .map(horse -> buildDashboardRow(
                        horse,
                        plansByHorse.getOrDefault(horse.horseId(), List.of()),
                        recentByHorse.getOrDefault(horse.horseId(), List.of()),
                        incidentsByHorse.getOrDefault(horse.horseId(), 0L),
                        specialtyByHorse.get(horse.horseId())))
                .toList();
    }

    private TrainerDashboardHorseResponse buildDashboardRow(DashboardHorseRow horse,
                                                            List<DashboardPlanRow> plans,
                                                            List<RecentWorkoutRow> recent,
                                                            long incidentCount,
                                                            HorseSpecialtyResponse specialty) {
        TrainerDashboardHorseResponse response = new TrainerDashboardHorseResponse();
        response.setHorseId(horse.horseId());
        response.setHorseName(horse.horseName());
        response.setBreed(horse.breed());
        response.setStallCode(horse.stallCode());

        fillPlanProgress(response, pickCurrentPlan(plans));
        fillRatingStats(response, recent);

        List<HorseAlertResponse> alerts = evaluateAlerts(recent, incidentCount);
        response.setAlertsCount(alerts.size());
        response.setAlertTitles(alerts.stream().map(HorseAlertResponse::getTitle).toList());
        response.setSpecialty(specialty);
        return response;
    }

    /**
     * Kế hoạch ưu tiên: ACTIVE, rồi UPCOMING, rồi kế hoạch đầu tiên còn lại; null nếu ngựa chưa có kế hoạch.
     * (Preferred plan: ACTIVE, then UPCOMING, then the first remaining; null if the horse has no plan.)
     */
    private DashboardPlanRow pickCurrentPlan(List<DashboardPlanRow> plans) {
        for (TrainingPlanStatus wanted : List.of(TrainingPlanStatus.ACTIVE, TrainingPlanStatus.UPCOMING)) {
            for (DashboardPlanRow plan : plans) {
                if (plan.status() == wanted) {
                    return plan;
                }
            }
        }
        return plans.isEmpty() ? null : plans.get(0);
    }

    private void fillPlanProgress(TrainerDashboardHorseResponse response, DashboardPlanRow plan) {
        if (plan == null) {
            response.setPlanStatus("NONE");
            response.setCourseName("No plan yet");
            response.setCompletedSessions(0);
            response.setTotalSessions(0);
            response.setProgressPercent(0.0);
            return;
        }

        int total = plan.totalSessions() != null ? plan.totalSessions() : 0;
        int completed = plan.completedSessions().intValue();

        response.setPlanId(plan.planId());
        response.setPlanStatus(plan.status().name());
        response.setStartDate(plan.startDate());
        response.setEndDate(plan.endDate());
        response.setCourseName(plan.courseName());
        response.setCompletedSessions(completed);
        response.setTotalSessions(total > 0 ? total : completed);
        response.setProgressPercent(total > 0 ? round1(completed * 100.0 / total) : 0.0);
    }

    /** Điểm phong độ mới nhất và trung bình 30 ngày. (Latest rating and the 30-day average.) */
    private void fillRatingStats(TrainerDashboardHorseResponse response, List<RecentWorkoutRow> recent) {
        if (recent.isEmpty()) {
            return;
        }
        response.setLatestPerformanceRating(recent.get(0).performanceRating());
        response.setAvgPerformanceRating30d(round1(recent.stream()
                .map(RecentWorkoutRow::performanceRating)
                .filter(rating -> rating != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0)));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
