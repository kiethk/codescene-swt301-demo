package com.rtms.backend.training.repository;

import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.stable.entity.Area;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.stable.enums.IncidentSeverity;
import com.rtms.backend.stable.repository.AreaRepository;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.dto.CourseSubjectResponse;
import com.rtms.backend.training.dto.DashboardHorseRow;
import com.rtms.backend.training.dto.DashboardPlanRow;
import com.rtms.backend.training.dto.GroomConflictRow;
import com.rtms.backend.training.dto.GroomWorkoutRow;
import com.rtms.backend.training.dto.HorseFitnessTrendItemResponse;
import com.rtms.backend.training.dto.HorseStallInfo;
import com.rtms.backend.training.dto.IncidentCountRow;
import com.rtms.backend.training.dto.JoinableCohortRow;
import com.rtms.backend.training.dto.LotHorseNameRow;
import com.rtms.backend.training.dto.PlanNamesRow;
import com.rtms.backend.training.dto.PlanSummaryResponse;
import com.rtms.backend.training.dto.PlanWorkoutItemResponse;
import com.rtms.backend.training.dto.RecentWorkoutRow;
import com.rtms.backend.training.dto.TrainingLotRow;
import com.rtms.backend.training.entity.Course;
import com.rtms.backend.training.entity.CourseSubject;
import com.rtms.backend.training.entity.HorseTrainingPlan;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.entity.TrainingWorkout;
import com.rtms.backend.training.enums.IntensityLevel;
import com.rtms.backend.training.enums.LotStatus;
import com.rtms.backend.training.enums.SubjectCategory;
import com.rtms.backend.training.enums.SurfaceType;
import com.rtms.backend.training.enums.TrainingDay;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import com.rtms.backend.training.enums.WorkoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chạy các câu JPQL JOIN/projection MỚI trên PostgreSQL thật. Test Mockito không bắt được lỗi ở đây (constructor
 * expression sai kiểu, GROUP BY thiếu cột, tham số null...), nên phải chạy thật.
 * (Runs the NEW JPQL JOIN/projection queries against a real PostgreSQL. Mockito tests cannot catch problems
 * here (a mistyped constructor expression, a missing GROUP BY column, null parameters...), so they must run
 * for real.)
 *
 * Mọi dữ liệu được tạo trong một transaction rồi rollback, nên không để lại gì trong DB.
 * (All data is created inside one transaction and rolled back, so nothing is left in the DB.)
 */
@SpringBootTest
@Transactional
class TrainingQueriesIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private AreaRepository areas;
    @Autowired private StableStallRepository stalls;
    @Autowired private HorseRepository horses;
    @Autowired private SubjectRepository subjects;
    @Autowired private CourseRepository courses;
    @Autowired private CourseSubjectRepository courseSubjects;
    @Autowired private HorseTrainingPlanRepository plans;
    @Autowired private TrainingLotRepository lots;
    @Autowired private TrainingWorkoutRepository workouts;
    @Autowired private GroomIncidentReportRepository incidents;

    private final LocalDate today = LocalDate.now();

    private User trainer;
    private User groom;
    private User owner;
    private User otherOwner;
    private Horse horse1;      // owned by `owner`, in an area managed by `trainer`
    private Horse horse2;      // owned by `otherOwner`, in the same area
    private Subject subject;
    private Course course;
    private HorseTrainingPlan plan;
    private TrainingLot todayLot;
    private TrainingWorkout workout1;   // horse1, SCHEDULED, today
    private TrainingWorkout workout2;   // horse2, SCHEDULED, today, same lot
    private TrainingWorkout done;       // horse1, COMPLETED, yesterday

    @BeforeEach
    void buildFixture() {
        trainer = users.findByEmail("trainer@rtms.com").orElseThrow();
        groom = users.findByEmail("groom@rtms.com").orElseThrow();
        owner = users.findByEmail("owner@rtms.com").orElseThrow();
        otherOwner = users.findByEmail("vet@rtms.com").orElseThrow();   // any other user works as "another owner"

        Area area = areas.findAll().stream().filter(a -> "A".equals(a.getCode())).findFirst().orElseThrow();
        area.setTrainerId(trainer.getId());
        areas.saveAndFlush(area);

        StableStall stall1 = stall(area, 901, "IT-901");
        StableStall stall2 = stall(area, 902, "IT-902");
        horse1 = horse("IT Horse One", owner, stall1);
        horse2 = horse("IT Horse Two", otherOwner, stall2);

        subject = new Subject();
        subject.setCategory(SubjectCategory.SPEED);
        subject.setName("IT Sprint");
        subject.setSurfaceType(SurfaceType.TURF);
        subject.setTargetDistanceMeters(BigDecimal.valueOf(800));
        subject.setIntensityLevel(IntensityLevel.HIGH);
        subject.setDurationMinutes(60);
        subject = subjects.saveAndFlush(subject);

        course = new Course();
        course.setName("IT Course " + System.nanoTime());
        course.setTotalSessions(4);
        course.setCreatedById(trainer.getId());
        course = courses.saveAndFlush(course);

        CourseSubject link = new CourseSubject();
        link.setCourseId(course.getId());
        link.setSubjectId(subject.getId());
        link.setOrderIndex(1);
        courseSubjects.saveAndFlush(link);

        plan = new HorseTrainingPlan();
        plan.setHorseId(horse1.getId());
        plan.setCourseId(course.getId());
        plan.setTrainerId(trainer.getId());
        plan.setStartDate(today.minusDays(2));
        plan.setEndDate(today.plusDays(10));
        plan.setTrainingDays(new LinkedHashSet<>(List.of(TrainingDay.MONDAY, TrainingDay.WEDNESDAY)));
        plan.setGroomId(groom.getId());
        plan.setStatus(TrainingPlanStatus.ACTIVE);
        plan = plans.saveAndFlush(plan);

        todayLot = lot(today, LocalTime.of(6, 0), LocalTime.of(7, 0));
        TrainingLot yesterdayLot = lot(today.minusDays(1), LocalTime.of(6, 0), LocalTime.of(7, 0));

        workout1 = workout(todayLot, horse1, WorkoutStatus.SCHEDULED);
        workout2 = workout(todayLot, horse2, WorkoutStatus.SCHEDULED);
        done = workout(yesterdayLot, horse1, WorkoutStatus.COMPLETED);
        done.setMaxHeartRate(230);
        done.setPerformanceRating(8);
        done.setActualDistanceMeters(BigDecimal.valueOf(800));
        workouts.saveAndFlush(done);
    }

    private StableStall stall(Area area, int number, String code) {
        StableStall stall = new StableStall();
        stall.setAreaId(area.getId());
        stall.setGroomId(groom.getId());
        stall.setStallNumber(number);
        stall.setStallCode(code);
        return stalls.saveAndFlush(stall);
    }

    private Horse horse(String name, User horseOwner, StableStall stall) {
        Horse horse = new Horse();
        horse.setName(name);
        horse.setOwnerId(horseOwner.getId());
        horse.setCurrentStallId(stall.getId());
        return horses.saveAndFlush(horse);
    }

    private TrainingLot lot(LocalDate date, LocalTime start, LocalTime end) {
        TrainingLot lot = new TrainingLot();
        lot.setTrainerId(trainer.getId());
        lot.setSubjectId(subject.getId());
        lot.setLotDate(date);
        lot.setStartTime(start);
        lot.setEndTime(end);
        lot.setMaxCapacity(6);
        lot.setStatus(LotStatus.SCHEDULED);
        return lots.saveAndFlush(lot);
    }

    private TrainingWorkout workout(TrainingLot lot, Horse horse, WorkoutStatus status) {
        TrainingWorkout workout = new TrainingWorkout();
        workout.setPlanId(plan.getId());
        workout.setLotId(lot.getId());
        workout.setHorseId(horse.getId());
        workout.setAssignedToId(groom.getId());
        workout.setStatus(status);
        return workouts.saveAndFlush(workout);
    }

    // =================================================================
    // Plans
    // =================================================================

    @Test
    void planSummariesJoinNamesAndCountWorkoutsPerStatus() {
        List<PlanSummaryResponse> mine = plans.findPlanSummaries(trainer.getId(), null);

        PlanSummaryResponse row = mine.stream().filter(p -> p.getPlanId().equals(plan.getId())).findFirst().orElseThrow();
        assertEquals("IT Horse One", row.getHorseName());
        assertEquals(course.getName(), row.getCourseName());
        assertEquals(TrainingPlanStatus.ACTIVE, row.getStatus());
        // The fixture puts 2 scheduled + 1 completed workout under the same plan
        assertEquals(3, row.getTotalSessions());
        assertEquals(1, row.getCompletedSessions());
        assertEquals(0, row.getCancelledSessions());
    }

    @Test
    void planSummariesAcceptNullAndNonNullFilters() {
        // trainer + horse
        assertEquals(1, plans.findPlanSummaries(trainer.getId(), horse1.getId()).stream()
                .filter(p -> p.getPlanId().equals(plan.getId())).count());
        // another horse has no plan of this trainer
        assertTrue(plans.findPlanSummaries(trainer.getId(), horse2.getId()).isEmpty());
        // every trainer, one horse
        assertEquals(1, plans.findPlanSummaries(null, horse1.getId()).size());
        // someone else's trainer id
        assertTrue(plans.findPlanSummaries(groom.getId(), null).isEmpty());
    }

    @Test
    void planNamesAndOwnership() {
        Optional<PlanNamesRow> names = plans.findPlanNames(plan.getId());
        assertTrue(names.isPresent());
        assertEquals("IT Horse One", names.get().horseName());
        assertEquals(course.getName(), names.get().courseName());

        assertTrue(plans.existsPlanOfOwner(plan.getId(), owner.getId()));
        assertFalse(plans.existsPlanOfOwner(plan.getId(), otherOwner.getId()));
    }

    @Test
    void stallInfoComesFromOneJoin() {
        List<HorseStallInfo> info = plans.findStallInfoByHorseIds(List.of(horse1.getId(), horse2.getId()));

        assertEquals(2, info.size());
        HorseStallInfo first = info.stream().filter(i -> i.horseId().equals(horse1.getId())).findFirst().orElseThrow();
        assertEquals("IT-901", first.stallCode());
        assertEquals(groom.getId(), first.stallGroomId());
        assertEquals("A", first.areaCode());
        assertEquals(trainer.getId(), first.areaTrainerId());
    }

    @Test
    void overlappingPlansAreFoundByDateRange() {
        List<TrainingPlanStatus> running = List.of(TrainingPlanStatus.ACTIVE, TrainingPlanStatus.UPCOMING);

        assertEquals(1, plans.findOverlappingPlans(List.of(horse1.getId()), running,
                today.plusDays(5), today.plusDays(8)).size());
        assertTrue(plans.findOverlappingPlans(List.of(horse1.getId()), running,
                today.plusDays(11), today.plusDays(20)).isEmpty(), "Starts after the plan ends");
        assertTrue(plans.findOverlappingPlans(List.of(horse2.getId()), running,
                today, today.plusDays(5)).isEmpty(), "Another horse");
        assertTrue(plans.findOverlappingPlans(List.of(horse1.getId()), List.of(TrainingPlanStatus.COMPLETED),
                today, today.plusDays(5)).isEmpty(), "Status filter");
    }

    @Test
    void joinableCohortsGroupByStartDateAndTrainingDays() {
        List<JoinableCohortRow> cohorts = plans.findJoinableCohorts(trainer.getId(), course.getId());

        assertEquals(1, cohorts.size());
        assertEquals(plan.getStartDate(), cohorts.get(0).startDate());
        assertEquals(Set.of(TrainingDay.MONDAY, TrainingDay.WEDNESDAY), cohorts.get(0).trainingDays());
        assertEquals(1L, cohorts.get(0).horseCount());
    }

    @Test
    void dashboardQueries() {
        List<DashboardHorseRow> dashboardHorses = plans.findDashboardHorses(trainer.getId());
        assertTrue(dashboardHorses.stream().anyMatch(h -> h.horseId().equals(horse1.getId())
                && "IT-901".equals(h.stallCode())));
        assertTrue(dashboardHorses.stream().anyMatch(h -> h.horseId().equals(horse2.getId())));

        List<DashboardPlanRow> dashboardPlans = plans.findDashboardPlans(trainer.getId());
        DashboardPlanRow row = dashboardPlans.stream().filter(p -> p.planId().equals(plan.getId())).findFirst().orElseThrow();
        assertEquals(course.getName(), row.courseName());
        assertEquals(4, row.totalSessions());
        assertEquals(1L, row.completedSessions());

        assertTrue(plans.existsHorseInTrainerArea(horse1.getId(), trainer.getId()));
        assertFalse(plans.existsHorseInTrainerArea(horse1.getId(), groom.getId()));
    }

    // =================================================================
    // Workouts
    // =================================================================

    @Test
    void planWorkoutItemsCarryLotAndOccupancy() {
        List<PlanWorkoutItemResponse> items = workouts.findPlanWorkoutItems(plan.getId());

        // The fixture puts all 3 workouts under the same plan id
        assertEquals(3, items.size());
        PlanWorkoutItemResponse scheduled = items.stream()
                .filter(i -> i.getWorkoutId().equals(workout1.getId())).findFirst().orElseThrow();
        assertEquals("IT Sprint", scheduled.getSubjectName());
        assertEquals(todayLot.getId(), scheduled.getLotId());
        assertEquals(today, scheduled.getLotDate());
        assertEquals(2, scheduled.getLotOccupancy(), "horse 1 and horse 2 share today's lot");
        assertEquals("SCHEDULED", scheduled.getStatus());
        // Ordered by lot date: yesterday's completed workout comes first
        assertEquals(done.getId(), items.get(0).getWorkoutId());
    }

    @Test
    void fitnessTrendAndRecentWorkoutsReadOnlyCompletedOnes() {
        List<HorseFitnessTrendItemResponse> trend =
                workouts.findFitnessTrend(horse1.getId(), today.minusDays(30), today);
        assertEquals(1, trend.size());
        assertEquals("IT Sprint", trend.get(0).getSubjectName());
        assertEquals(today.minusDays(1), trend.get(0).getDate());
        assertEquals(8, trend.get(0).getPerformanceRating());

        assertTrue(workouts.findFitnessTrend(horse1.getId(), today, today).isEmpty(), "Outside the range");

        List<RecentWorkoutRow> recent = workouts.findRecentCompletedWorkouts(
                List.of(horse1.getId(), horse2.getId()), today.minusDays(30));
        assertEquals(1, recent.size());
        assertEquals(230, recent.get(0).maxHeartRate());
        assertEquals(LocalTime.of(7, 0), recent.get(0).lotEndTime());
    }

    @Test
    void groomWorkoutRowsAndConflicts() {
        List<GroomWorkoutRow> rows = workouts.findGroomWorkoutRows(groom.getId(), today);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "IT Horse One".equals(r.horseName())
                && "IT Sprint".equals(r.subjectName()) && LocalTime.of(6, 0).equals(r.startTime())));

        // Horse 1 would clash with the groom in today's lot, because the groom already leads horse 2 there
        List<GroomConflictRow> conflicts = workouts.findGroomConflicts(horse1.getId(), groom.getId(), today);
        assertEquals(1, conflicts.size());
        assertEquals("IT Horse Two", conflicts.get(0).otherHorseName());
        assertEquals(todayLot.getId(), conflicts.get(0).lotId());

        assertTrue(workouts.findGroomConflicts(horse1.getId(), trainer.getId(), today).isEmpty());
    }

    @Test
    void ownerHasWorkoutInLotOnlyForOwnHorses() {
        assertTrue(workouts.existsActiveWorkoutOfOwnerInLot(todayLot.getId(), owner.getId()));
        assertTrue(workouts.existsActiveWorkoutOfOwnerInLot(todayLot.getId(), otherOwner.getId()));
        assertFalse(workouts.existsActiveWorkoutOfOwnerInLot(todayLot.getId(), groom.getId()));
    }

    // =================================================================
    // Lots
    // =================================================================

    @Test
    void lotRowsPerRole() {
        List<TrainingLotRow> trainerRows = lots.findTrainerLotRows(trainer.getId(), today, today, false);
        assertEquals(1, trainerRows.size());
        TrainingLotRow row = trainerRows.get(0);
        assertEquals("IT Sprint", row.subjectName());
        assertEquals(60, row.durationMinutes());
        assertEquals(2L, row.occupied());
        assertEquals(trainer.getId(), row.trainerId());

        assertEquals(1, lots.findGroomLotRows(groom.getId(), today, today).size());
        assertEquals(1, lots.findOwnerLotRows(owner.getId(), today, today).size());
        assertTrue(lots.findOwnerLotRows(groom.getId(), today, today).isEmpty(), "Not an owner of any horse");
        assertTrue(lots.findOwnerLotRows(owner.getId(), today.plusDays(1), today.plusDays(3)).isEmpty());

        assertTrue(lots.findLotRowById(todayLot.getId()).isPresent());
        assertTrue(lots.findLotRowById(-1L).isEmpty());
    }

    @Test
    void cancelledLotsAreHiddenUnlessRequested() {
        todayLot.setStatus(LotStatus.CANCELLED);
        lots.saveAndFlush(todayLot);

        assertTrue(lots.findTrainerLotRows(trainer.getId(), today, today, false).isEmpty());
        assertEquals(1, lots.findTrainerLotRows(trainer.getId(), today, today, true).size());
        assertTrue(lots.findGroomLotRows(groom.getId(), today, today).isEmpty());
        assertTrue(lots.findOwnerLotRows(owner.getId(), today, today).isEmpty());
    }

    @Test
    void horseNamesOfOwnerExcludeOtherOwnersHorses() {
        List<Long> lotIds = List.of(todayLot.getId());

        List<LotHorseNameRow> all = lots.findActiveHorseNames(lotIds);
        assertEquals(List.of("IT Horse One", "IT Horse Two"), all.stream().map(LotHorseNameRow::horseName).toList());

        List<LotHorseNameRow> mine = lots.findActiveHorseNamesOfOwner(lotIds, owner.getId());
        assertEquals(List.of("IT Horse One"), mine.stream().map(LotHorseNameRow::horseName).toList());
    }

    // =================================================================
    // Courses and incidents
    // =================================================================

    @Test
    void courseSubjectsAreJoinedInOrder() {
        List<Subject> ordered = courseSubjects.findSubjectsOfCourseInOrder(course.getId());
        assertEquals(1, ordered.size());
        assertEquals("IT Sprint", ordered.get(0).getName());
        assertEquals(SubjectCategory.SPEED, ordered.get(0).getCategory());

        List<CourseSubjectResponse> responses = courseSubjects.findCourseSubjectResponses(course.getId());
        assertEquals(1, responses.size());
        assertEquals("IT Sprint", responses.get(0).getSubjectName());
        assertEquals(60, responses.get(0).getDurationMinutes());
        assertEquals(1, responses.get(0).getOrderIndex());

        assertEquals(1L, courseSubjects.countByCourseId(course.getId()));
    }

    @Test
    void subjectsCanBeFilteredByCategory() {
        assertTrue(subjects.findByCategory(SubjectCategory.SPEED).stream()
                .anyMatch(s -> s.getId().equals(subject.getId())));
        assertTrue(subjects.findByCategory(SubjectCategory.RECOVERY).stream()
                .noneMatch(s -> s.getId().equals(subject.getId())));
    }

    @Test
    void incidentCountsAreGroupedPerHorse() {
        incidents.saveAndFlush(new GroomIncidentReport(groom.getId(), horse1.getId(), "Limping", "Left foreleg",
                null, IncidentSeverity.HIGH));
        incidents.saveAndFlush(new GroomIncidentReport(groom.getId(), horse1.getId(), "Limping again", "Left foreleg",
                null, IncidentSeverity.HIGH));

        List<IncidentCountRow> counts = incidents.countIncidentsByHorseIds(
                List.of(horse1.getId(), horse2.getId()), LocalDateTime.now().minusDays(14));

        assertEquals(1, counts.size(), "Horse 2 has no incident and does not appear");
        assertEquals(horse1.getId(), counts.get(0).horseId());
        assertEquals(2L, counts.get(0).count());
    }
}
