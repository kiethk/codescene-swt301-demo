package com.rtms.backend.training.repository;

import com.rtms.backend.training.dto.DashboardHorseRow;
import com.rtms.backend.training.dto.DashboardPlanRow;
import com.rtms.backend.training.dto.HorseStallInfo;
import com.rtms.backend.training.dto.JoinableCohortRow;
import com.rtms.backend.training.dto.PlanNamesRow;
import com.rtms.backend.training.dto.PlanSummaryResponse;
import com.rtms.backend.training.entity.HorseTrainingPlan;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HorseTrainingPlanRepository extends JpaRepository<HorseTrainingPlan, Long> {

    List<HorseTrainingPlan> findByHorseIdAndStatusInOrderByEndDateDesc(Long horseId, Collection<TrainingPlanStatus> statuses);

    /**
     * Các kế hoạch ĐÃ TỚI NGÀY BẮT ĐẦU nhưng vẫn còn nhãn UPCOMING (job nửa đêm dùng để dọn).
     * (Plans whose start date has arrived but are still labelled UPCOMING; the midnight job cleans them.)
     */
    List<HorseTrainingPlan> findByStatusAndStartDateLessThanEqual(TrainingPlanStatus status,
                                                                   LocalDate date);

    // =================================================================
    // DANH SÁCH / CHI TIẾT KẾ HOẠCH (List / detail of plans)
    // =================================================================

    /**
     * Danh sách kế hoạch kèm tên ngựa, tên khóa và ba con số tiến độ, tất cả trong MỘT câu SQL.
     * trainerId = null nghĩa là mọi Trainer; horseId = null nghĩa là mọi ngựa. Kế hoạch mới nhất lên đầu.
     * (Plan list with horse name, course name and the three progress counters in ONE SQL statement.
     * trainerId = null means every trainer; horseId = null means every horse. Newest plan first.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.PlanSummaryResponse(
                      p.id, p.horseId, h.name, p.courseId, c.name, p.startDate, p.endDate, p.status,
                      COUNT(w),
                      COALESCE(SUM(CASE WHEN w.status = com.rtms.backend.training.enums.WorkoutStatus.COMPLETED
                                        THEN 1L ELSE 0L END), 0L),
                      COALESCE(SUM(CASE WHEN w.status = com.rtms.backend.training.enums.WorkoutStatus.CANCELLED
                                        THEN 1L ELSE 0L END), 0L))
           FROM HorseTrainingPlan p
           JOIN Horse h ON p.horseId = h.id
           JOIN Course c ON p.courseId = c.id
           LEFT JOIN TrainingWorkout w ON w.planId = p.id
           WHERE (:trainerId IS NULL OR p.trainerId = :trainerId)
             AND (:horseId IS NULL OR p.horseId = :horseId)
           GROUP BY p.id, p.horseId, h.name, p.courseId, c.name, p.startDate, p.endDate, p.status
           ORDER BY p.startDate DESC, p.id DESC
           """)
    List<PlanSummaryResponse> findPlanSummaries(@Param("trainerId") Long trainerId,
                                                @Param("horseId") Long horseId);

    /** Tên ngựa và tên khóa của một kế hoạch. (Horse and course names of one plan.) */
    @Query("""
           SELECT new com.rtms.backend.training.dto.PlanNamesRow(h.name, c.name)
           FROM HorseTrainingPlan p
           JOIN Horse h ON p.horseId = h.id
           JOIN Course c ON p.courseId = c.id
           WHERE p.id = :planId
           """)
    Optional<PlanNamesRow> findPlanNames(@Param("planId") Long planId);

    /** Kế hoạch này có phải của ngựa thuộc owner không? (Does this plan belong to a horse of this owner?) */
    @Query("""
           SELECT COUNT(p) > 0
           FROM HorseTrainingPlan p
           JOIN Horse h ON p.horseId = h.id
           WHERE p.id = :planId AND h.ownerId = :ownerId
           """)
    boolean existsPlanOfOwner(@Param("planId") Long planId, @Param("ownerId") Long ownerId);

    // =================================================================
    // LẬP KẾ HOẠCH (Creating plans)
    // =================================================================

    /**
     * Chuồng và khu của nhiều chiến mã bằng MỘT câu JOIN. Ngựa chưa có chuồng sẽ không xuất hiện.
     * (Stall and area of many horses with ONE JOIN. A horse without a stall does not appear.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.HorseStallInfo(h.id, ss.stallCode, ss.groomId, a.code, a.trainerId)
           FROM Horse h
           JOIN StableStall ss ON h.currentStallId = ss.id
           JOIN Area a ON ss.areaId = a.id
           WHERE h.id IN :horseIds
           """)
    List<HorseStallInfo> findStallInfoByHorseIds(@Param("horseIds") Collection<Long> horseIds);

    /**
     * BR-01: các kế hoạch đang chạy của nhiều ngựa có khoảng ngày chồng lên [startDate, endDate].
     * (BR-01: running plans of many horses whose date range overlaps [startDate, endDate].)
     */
    @Query("""
           SELECT p FROM HorseTrainingPlan p
           WHERE p.horseId IN :horseIds
             AND p.status IN :statuses
             AND p.startDate <= :endDate
             AND p.endDate >= :startDate
           ORDER BY p.endDate DESC
           """)
    List<HorseTrainingPlan> findOverlappingPlans(@Param("horseIds") Collection<Long> horseIds,
                                                 @Param("statuses") Collection<TrainingPlanStatus> statuses,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    /**
     * API gợi ý nhóm (joinable-cohorts). Nhóm không phải entity: là các plan cùng (trainerId, courseId,
     * startDate) và cùng trainingDays, nên chỉ cần GROUP BY.
     * (Joinable-cohorts API. A cohort is not an entity: it is the plans sharing (trainerId, courseId,
     * startDate) and trainingDays, so a GROUP BY is enough.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.JoinableCohortRow(p.startDate, p.trainingDays, COUNT(p.id))
           FROM HorseTrainingPlan p
           WHERE p.trainerId = :trainerId
             AND p.courseId = :courseId
             AND p.status IN (com.rtms.backend.training.enums.TrainingPlanStatus.UPCOMING,
                              com.rtms.backend.training.enums.TrainingPlanStatus.ACTIVE)
           GROUP BY p.startDate, p.trainingDays
           ORDER BY p.startDate ASC
           """)
    List<JoinableCohortRow> findJoinableCohorts(@Param("trainerId") Long trainerId,
                                                @Param("courseId") Long courseId);

    // =================================================================
    // DASHBOARD CỦA TRAINER (Trainer dashboard)
    // =================================================================

    /** Chiến mã trong các khu của Trainer, kèm mã chuồng. (Horses in the trainer areas, with stall code.) */
    @Query("""
           SELECT new com.rtms.backend.training.dto.DashboardHorseRow(h.id, h.name, h.breed, ss.stallCode)
           FROM Horse h
           JOIN StableStall ss ON h.currentStallId = ss.id
           JOIN Area a ON ss.areaId = a.id
           WHERE a.trainerId = :trainerId
           ORDER BY h.id ASC
           """)
    List<DashboardHorseRow> findDashboardHorses(@Param("trainerId") Long trainerId);

    /**
     * Mọi kế hoạch của Trainer kèm tên khóa và số buổi đã hoàn thành (sub-query), không cần
     * tải toàn bộ bảng rồi lọc bằng stream.
     * (Every plan of the trainer with course name and completed-session count (sub-query), so no
     * whole-table load followed by stream filtering.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.DashboardPlanRow(
                      p.id, p.horseId, p.status, p.startDate, p.endDate, c.name, c.totalSessions,
                      (SELECT COUNT(w) FROM TrainingWorkout w
                       WHERE w.planId = p.id
                         AND w.status = com.rtms.backend.training.enums.WorkoutStatus.COMPLETED))
           FROM HorseTrainingPlan p
           JOIN Course c ON p.courseId = c.id
           WHERE p.trainerId = :trainerId
           ORDER BY p.id ASC
           """)
    List<DashboardPlanRow> findDashboardPlans(@Param("trainerId") Long trainerId);

    /** Ngựa có nằm trong một khu của Trainer không? (Is the horse in one of the trainer areas?) */
    @Query("""
           SELECT COUNT(h) > 0
           FROM Horse h
           JOIN StableStall ss ON h.currentStallId = ss.id
           JOIN Area a ON ss.areaId = a.id
           WHERE h.id = :horseId AND a.trainerId = :trainerId
           """)
    boolean existsHorseInTrainerArea(@Param("horseId") Long horseId, @Param("trainerId") Long trainerId);
}
