package com.rtms.backend.training.service;

import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.CompleteWorkoutRequest;
import com.rtms.backend.training.dto.GroomConflictRow;
import com.rtms.backend.training.dto.PlanWorkoutItemResponse;
import com.rtms.backend.training.entity.HorseTrainingPlan;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.entity.TrainingWorkout;
import com.rtms.backend.training.enums.LotStatus;
import com.rtms.backend.training.enums.TrainingDay;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import com.rtms.backend.training.enums.WorkoutStatus;
import com.rtms.backend.training.repository.HorseTrainingPlanRepository;
import com.rtms.backend.training.repository.SubjectRepository;
import com.rtms.backend.training.repository.TrainingLotRepository;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mọi thao tác trên BUỔI TẬP (workout): sinh buổi tập khi lập kế hoạch, Trainer đóng buổi tập,
 * hủy buổi tập tương lai khi ngựa bị chặn tập, và đổi Groom cho buổi tập chưa diễn ra.
 * (Everything done to a WORKOUT: generating workouts when a plan is created, the trainer closing a
 * workout, cancelling future workouts when a horse is blocked, and re-assigning the groom of workouts
 * that have not happened yet.)
 *
 * Service này chỉ phụ thuộc repository và TrainingLotService, KHÔNG phụ thuộc ngược các service khác,
 * nên TrainingDecisionService gọi được mà không tạo vòng phụ thuộc.
 * (This service depends only on repositories and TrainingLotService, never back on other services, so
 * TrainingDecisionService can call it without creating a dependency cycle.)
 */
@Service
public class TrainingWorkoutService {

    private final TrainingWorkoutRepository workoutRepository;
    private final HorseTrainingPlanRepository planRepository;
    private final TrainingLotRepository lotRepository;
    private final SubjectRepository subjectRepository;
    private final TrainingLotService lotService;

    public TrainingWorkoutService(TrainingWorkoutRepository workoutRepository,
                                  HorseTrainingPlanRepository planRepository,
                                  TrainingLotRepository lotRepository,
                                  SubjectRepository subjectRepository,
                                  TrainingLotService lotService) {
        this.workoutRepository = workoutRepository;
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
        this.subjectRepository = subjectRepository;
        this.lotService = lotService;
    }

    // =================================================================
    // ĐỌC (Read)
    // =================================================================

    /**
     * Buổi tập của một kế hoạch kèm thông tin lot, tên bài tập và số ngựa trong lot, bằng MỘT truy vấn.
     * (A plan workouts with lot info, subject name and the lot horse count, in ONE query.)
     */
    @Transactional(readOnly = true)
    public List<PlanWorkoutItemResponse> buildWorkoutItems(Long planId) {
        return workoutRepository.findPlanWorkoutItems(planId);
    }

    // =================================================================
    // SINH BUỔI TẬP KHI LẬP KẾ HOẠCH (Generate workouts when a plan is created)
    // =================================================================

    /**
     * Lịch cần sinh: ai dạy, bắt đầu khi nào, những thứ nào trong tuần, bao nhiêu buổi, các bài xoay vòng.
     * (The schedule to generate: who teaches, when it starts, which weekdays, how many sessions, and
     * the rotating subjects.)
     */
    public record PlanSchedule(Long trainerId,
                               LocalDate startDate,
                               Set<TrainingDay> trainingDays,
                               int totalSessions,
                               List<Subject> orderedSubjects) {
    }

    /**
     * SINH LOT + BUỔI TẬP cho mọi chiến mã đã ghi danh. Trả về buổi tập theo từng con ngựa.
     * (GENERATE LOTS + WORKOUTS for every enrolled horse. Returns the workouts per horse.)
     *
     * findOrCreateLot nằm BÊN TRONG vòng lặp ngựa: khi một lot đầy giữa chừng hoặc vướng BR-09, con tiếp
     * theo tự động tràn sang lot khác. Nhóm ĐƯỢC PHÉP tách: giá trị của cơ chế nhóm là "cùng bài vào
     * cùng ngày", không phải "luôn chung một lot".
     * (findOrCreateLot sits INSIDE the horse loop: when a lot fills up or hits BR-09, the next horse
     * overflows into another lot. A group MAY split: its value is "same subject on the same day", not
     * "always the same lot".)
     */
    @Transactional
    public Map<Long, List<PlanWorkoutItemResponse>> createWorkouts(PlanSchedule schedule,
                                                                   List<EnrolledHorse> enrolled,
                                                                   Map<Long, HorseTrainingPlan> plansByHorse) {
        Map<Long, List<PlanWorkoutItemResponse>> workoutsByHorse = new LinkedHashMap<>();
        enrolled.forEach(en -> workoutsByHorse.put(en.horse().getId(), new ArrayList<>()));

        LocalDate cursor = schedule.startDate();
        int sessionIndex = 0;

        while (sessionIndex < schedule.totalSessions()) {
            TrainingDay day = TrainingDay.valueOf(cursor.getDayOfWeek().name());

            if (schedule.trainingDays().contains(day)) {
                // Xoay vòng bài tập: orderedSubjects[i % n] (Rotate subjects: orderedSubjects[i % n])
                Subject subject = schedule.orderedSubjects()
                        .get(sessionIndex % schedule.orderedSubjects().size());
                createSessionWorkouts(schedule.trainerId(), cursor, subject, enrolled,
                        plansByHorse, workoutsByHorse);
                sessionIndex++;
            }

            if (sessionIndex < schedule.totalSessions()) {
                cursor = cursor.plusDays(1);
            }
        }
        return workoutsByHorse;
    }

    /** Một buổi học: mỗi con ngựa một workout. (One session: one workout per horse.) */
    private void createSessionWorkouts(Long trainerId, LocalDate date, Subject subject,
                                       List<EnrolledHorse> enrolled,
                                       Map<Long, HorseTrainingPlan> plansByHorse,
                                       Map<Long, List<PlanWorkoutItemResponse>> workoutsByHorse) {
        for (EnrolledHorse en : enrolled) {
            Long horseId = en.horse().getId();
            TrainingLot lot = lotService.findOrCreateLot(trainerId, date, subject, en.groomId());
            TrainingWorkout saved = saveWorkout(plansByHorse.get(horseId).getId(), lot, en);
            workoutsByHorse.get(horseId).add(new PlanWorkoutItemResponse(saved, lot, subject.getName()));
        }
    }

    /**
     * saveAndFlush là BẮT BUỘC: findOrCreateLot của con NGỰA KẾ TIẾP đếm sức chứa và kiểm tra BR-09 bằng
     * câu query, nên workout này phải xuống DB trước.
     * (saveAndFlush is REQUIRED: findOrCreateLot for the NEXT horse counts capacity and checks BR-09 with
     * a query, so this workout must reach the DB first.)
     */
    private TrainingWorkout saveWorkout(Long planId, TrainingLot lot, EnrolledHorse en) {
        TrainingWorkout workout = new TrainingWorkout();
        workout.setPlanId(planId);
        workout.setLotId(lot.getId());
        workout.setHorseId(en.horse().getId());
        workout.setAssignedToId(en.groomId());
        workout.setStatus(WorkoutStatus.SCHEDULED);
        return workoutRepository.saveAndFlush(workout);
    }

    // =================================================================
    // ĐÓNG BUỔI TẬP (TRAINER GHI NHẬN KẾT QUẢ)
    // (CLOSING A WORKOUT — THE TRAINER RECORDS RESULTS)
    // =================================================================

    /**
     * Trainer đóng một buổi tập và ghi nhận chỉ số chuyên môn.
     * (The trainer closes a workout and records professional metrics.)
     *
     * Khác với việc Groom tick hoàn thành: groom_daily_tasks.isCompleted chỉ là "có làm / chưa làm",
     * còn training_workouts.status do Trainer bấm, kèm chỉ số và nhận xét.
     * (Different from the groom ticking done: groom_daily_tasks.isCompleted is only "done / not done",
     * while training_workouts.status is set by the trainer with metrics and feedback.)
     */
    @Transactional
    public PlanWorkoutItemResponse completeWorkout(Long workoutId,
                                                   CompleteWorkoutRequest request,
                                                   AuthenticatedUser currentUser) {
        TrainingWorkout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new RuntimeException("Workout #" + workoutId + " not found"));
        HorseTrainingPlan plan = planRepository.findById(workout.getPlanId())
                .orElseThrow(() -> new RuntimeException("Plan of this workout not found"));

        assertCanComplete(workout, plan, request, currentUser);
        applyResults(workout, request);
        TrainingWorkout saved = workoutRepository.saveAndFlush(workout);

        updatePlanProgress(plan);
        TrainingLot lot = updateLotProgress(saved);

        String subjectName = subjectRepository.findById(lot.getSubjectId())
                .map(Subject::getName)
                .orElse("#" + lot.getSubjectId());
        return new PlanWorkoutItemResponse(saved, lot, subjectName);
    }

    private void assertCanComplete(TrainingWorkout workout, HorseTrainingPlan plan,
                                   CompleteWorkoutRequest request, AuthenticatedUser currentUser) {
        if (!plan.getTrainerId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException(
                    "Only the trainer in charge of this plan can record workout results!");
        }
        if (workout.getStatus() == WorkoutStatus.CANCELLED) {
            throw new IllegalStateException("This workout was cancelled, so results cannot be recorded!");
        }
        if (request.getPerformanceRating() != null
                && (request.getPerformanceRating() < 1 || request.getPerformanceRating() > 10)) {
            throw new IllegalArgumentException("Performance rating must be between 1 and 10!");
        }
    }

    private void applyResults(TrainingWorkout workout, CompleteWorkoutRequest request) {
        workout.setActualDistanceMeters(request.getActualDistanceMeters());
        workout.setActualDurationMinutes(request.getActualDurationMinutes());
        workout.setTopSpeedKmh(request.getTopSpeedKmh());
        workout.setAverageSpeedKmh(request.getAverageSpeedKmh());
        workout.setAverageHeartRate(request.getAverageHeartRate());
        workout.setMaxHeartRate(request.getMaxHeartRate());
        workout.setRecoveryHeartRate(request.getRecoveryHeartRate());
        workout.setPerformanceRating(request.getPerformanceRating());
        workout.setTrainerFeedback(request.getTrainerFeedback());
        workout.setVideoUrl(request.getVideoUrl());
        workout.setStatus(WorkoutStatus.COMPLETED);
    }

    /**
     * Hoàn thành một buổi nghĩa là khóa ĐÃ BẮT ĐẦU, nên UPCOMING chuyển ACTIVE ngay tại đây thay vì chỉ
     * trông vào job nửa đêm (server tắt đúng đêm đó thì plan kẹt UPCOMING mãi). Buổi cuối cùng của khóa
     * thì plan tự COMPLETED; buổi CANCELLED được coi là đã giải quyết xong nên không chặn việc đóng khóa.
     * (Completing a workout means the course HAS STARTED, so UPCOMING becomes ACTIVE here instead of
     * relying only on the midnight job (if the server is down that night the plan stays UPCOMING
     * forever). The last workout closes the plan as COMPLETED; CANCELLED workouts count as resolved so
     * they do not block closing the course.)
     */
    private void updatePlanProgress(HorseTrainingPlan plan) {
        if (plan.getStatus() == TrainingPlanStatus.UPCOMING) {
            plan.setStatus(TrainingPlanStatus.ACTIVE);
        }

        long remaining = workoutRepository.countByPlanIdAndStatusNotIn(
                plan.getId(), List.of(WorkoutStatus.COMPLETED, WorkoutStatus.CANCELLED));
        if (remaining == 0
                && plan.getStatus() != TrainingPlanStatus.COMPLETED
                && plan.getStatus() != TrainingPlanStatus.CANCELLED) {
            plan.setStatus(TrainingPlanStatus.COMPLETED);
        }
        planRepository.save(plan);
    }

    /**
     * Khi mọi buổi trong lot đã hoàn thành (hoặc bị hủy), lot tự chuyển COMPLETED.
     * (When every workout in the lot is completed or cancelled, the lot becomes COMPLETED.)
     */
    private TrainingLot updateLotProgress(TrainingWorkout saved) {
        TrainingLot lot = lotRepository.findById(saved.getLotId())
                .orElseThrow(() -> new RuntimeException("Lot of this workout not found"));

        long remainingInLot = workoutRepository.countByLotIdAndStatusNotIn(
                lot.getId(), List.of(WorkoutStatus.COMPLETED, WorkoutStatus.CANCELLED));
        if (remainingInLot == 0
                && lot.getStatus() != LotStatus.COMPLETED
                && lot.getStatus() != LotStatus.CANCELLED) {
            lot.setStatus(LotStatus.COMPLETED);
            lotRepository.save(lot);
        }
        return lot;
    }

    // =================================================================
    // CASCADE KHI NGỰA BỊ CHẶN TẬP (Cascade when a horse is blocked from training)
    // =================================================================

    /**
     * HỦY TOÀN BỘ HUẤN LUYỆN TƯƠNG LAI CỦA MỘT CHIẾN MÃ.
     * (CANCEL ALL FUTURE TRAINING OF ONE HORSE.)
     *
     * Chỉ gọi qua TrainingDecisionService.block, mỗi khi ngựa bị chặn tập. Theo thiết kế đã chốt:
     * (Only called through TrainingDecisionService.block whenever a horse is blocked. By design:)
     *   1. Hủy mọi buổi tập SCHEDULED từ hôm nay trở đi. (Cancel every SCHEDULED workout from today on.)
     *   2. Lot nào rỗng sau khi hủy thì hủy luôn, trả lại khe giờ. (A lot left empty is cancelled too,
     *      returning its time slot.)
     *   3. Plan đang chạy chuyển CANCELLED (không tạm dừng): kế hoạch dở dang sau thời gian nghỉ hiếm khi còn
     *      đúng. (Running plans become CANCELLED (not paused): an unfinished plan rarely still fits after
     *      the rest period.)
     *
     * @return số buổi tập đã hủy (number of workouts cancelled)
     */
    @Transactional
    public int cancelFutureTrainingForHorse(Long horseId, String reason) {
        List<TrainingWorkout> future =
                workoutRepository.findFutureScheduledByHorse(horseId, LocalDate.now());

        future.forEach(w -> w.setStatus(WorkoutStatus.CANCELLED));
        workoutRepository.saveAll(future);
        workoutRepository.flush();   // để cancelLotIfEmpty đếm đúng (so cancelLotIfEmpty counts correctly)

        // Lot rỗng -> hủy, nếu không khung giờ vàng bị chiếm bởi "lot ma".
        // (Empty lot -> cancel, otherwise the golden window stays occupied by a "ghost lot".)
        Set<Long> touchedLotIds = future.stream()
                .map(TrainingWorkout::getLotId)
                .collect(Collectors.toCollection(HashSet::new));
        touchedLotIds.forEach(lotService::cancelLotIfEmpty);

        cancelRunningPlans(horseId, reason);
        return future.size();
    }

    private void cancelRunningPlans(Long horseId, String reason) {
        List<HorseTrainingPlan> plans = planRepository.findByHorseIdAndStatusInOrderByEndDateDesc(
                horseId, List.of(TrainingPlanStatus.ACTIVE, TrainingPlanStatus.UPCOMING));
        String suffix = "[Auto-cancelled] " + (reason != null ? reason : "Horse is blocked from training");

        for (HorseTrainingPlan plan : plans) {
            plan.setStatus(TrainingPlanStatus.CANCELLED);
            plan.setNotes(plan.getNotes() == null ? suffix : plan.getNotes() + " | " + suffix);
        }
        planRepository.saveAll(plans);
    }

    // =================================================================
    // ĐỒNG BỘ GROOM KHI ĐỔI CHUỒNG / ĐỔI NGƯỜI PHỤ TRÁCH
    // (Sync the groom when the stall or the person in charge changes)
    // =================================================================

    /**
     * Chuyển mọi buổi tập CHƯA diễn ra của một chiến mã sang Groom mới.
     * (Move every not-yet-held workout of a horse to the new groom.)
     *
     * VÌ SAO CẦN: workout.assignedToId là ẢNH CHỤP lấy từ stall.groomId lúc tạo kế hoạch. Đổi Groom của
     * chuồng, hoặc chuyển ngựa sang chuồng khác, KHÔNG tự cập nhật các workout đã sinh: Groom cũ vẫn thấy
     * việc không còn của mình, Groom mới không thấy việc của mình, và BR-09 có thể bị vi phạm ngoài đời
     * mà dữ liệu vẫn trông hợp lệ.
     * (WHY: workout.assignedToId is a SNAPSHOT taken from stall.groomId when the plan was created.
     * Changing the stall groom or moving the horse does NOT update workouts already generated: the old
     * groom still sees work that is no longer theirs, the new groom does not see theirs, and BR-09 can
     * be violated in real life while the data still looks valid.)
     *
     * @param newGroomId Groom mới; null khi gỡ Groom khỏi chuồng hoặc gỡ ngựa khỏi chuồng: buổi tập thành
     *                   "chưa phân công" để Trainer thấy và xử lý. (New groom; null when the groom or the
     *                   horse is removed from the stall: workouts become "unassigned" so the trainer sees them.)
     * @return số buổi tập đã chuyển (number of workouts moved)
     * @throws IllegalStateException nếu bất kỳ lot nào vi phạm BR-09 (if any lot violates BR-09)
     */
    @Transactional
    public int reassignFutureWorkoutsToGroom(Long horseId, Long newGroomId) {
        List<TrainingWorkout> future =
                workoutRepository.findFutureScheduledByHorse(horseId, LocalDate.now());

        if (future.isEmpty()) {
            return 0;   // ngựa chưa có kế hoạch nào (the horse has no plan yet)
        }

        // Không ai phụ trách thì không thể trùng với ai, nên bỏ qua kiểm tra khi newGroomId == null.
        // (Nobody in charge cannot clash with anybody, so the check is skipped when newGroomId == null.)
        if (newGroomId != null) {
            assertNoGroomConflict(horseId, newGroomId);
        }

        // Buổi đã COMPLETED giữ Groom cũ: đó là LỊCH SỬ. findFutureScheduledByHorse chỉ trả buổi SCHEDULED.
        // (COMPLETED workouts keep the old groom: that is HISTORY. findFutureScheduledByHorse only
        // returns SCHEDULED workouts.)
        future.forEach(w -> w.setAssignedToId(newGroomId));
        workoutRepository.saveAll(future);
        return future.size();
    }

    /**
     * Gom HẾT các lot bị vướng rồi mới báo lỗi, để người dùng thấy đủ trong một lần.
     * (Collects ALL clashing lots before failing, so the user sees them all at once.)
     */
    private void assertNoGroomConflict(Long horseId, Long newGroomId) {
        List<GroomConflictRow> conflicts =
                workoutRepository.findGroomConflicts(horseId, newGroomId, LocalDate.now());
        if (conflicts.isEmpty()) {
            return;
        }

        String details = conflicts.stream()
                .map(this::describeGroomConflict)
                .collect(Collectors.joining(System.lineSeparator()));
        throw new IllegalStateException(String.format(
                "Cannot assign this horse to that groom: a groom cannot lead 2 horses in the same lot (BR-09).%n%s%n"
              + "Move the lot to another time slot, or choose another groom.",
                details));
    }

    /** Dựng câu mô tả một lot bị vướng BR-09. (Builds the description of one lot that violates BR-09.) */
    private String describeGroomConflict(GroomConflictRow row) {
        return String.format("  · %s at %s (lot #%d): this groom is already leading '%s'",
                row.lotDate(), row.startTime(), row.lotId(), row.otherHorseName());
    }
}
