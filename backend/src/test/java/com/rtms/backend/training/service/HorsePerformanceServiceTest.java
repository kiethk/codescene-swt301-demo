package com.rtms.backend.training.service;

import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.notification.dto.HorseAlertResponse;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.training.dto.DashboardHorseRow;
import com.rtms.backend.training.dto.DashboardPlanRow;
import com.rtms.backend.training.dto.HorseFitnessTrendItemResponse;
import com.rtms.backend.training.dto.IncidentCountRow;
import com.rtms.backend.training.dto.RecentWorkoutRow;
import com.rtms.backend.training.dto.TrainerDashboardHorseResponse;
import com.rtms.backend.training.enums.TrainingPlanStatus;
import com.rtms.backend.training.repository.HorseTrainingPlanRepository;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HorsePerformanceServiceTest {

    @Mock
    private TrainingWorkoutRepository workoutRepository;

    @Mock
    private HorseTrainingPlanRepository planRepository;

    @Mock
    private GroomIncidentReportRepository incidentReportRepository;

    @Mock
    private HorseRepository horseRepository;

    private HorsePerformanceService service;

    private final AuthenticatedUser manager = new AuthenticatedUser(1L, "manager@example.com", "CLUB_MANAGER");

    @BeforeEach
    void setUp() {
        service = new HorsePerformanceService(workoutRepository, planRepository, incidentReportRepository, horseRepository);
    }

    private Horse horse(Long id, Long ownerId) {
        Horse horse = new Horse();
        horse.setId(id);
        horse.setName("Horse " + id);
        horse.setOwnerId(ownerId);
        return horse;
    }

    private RecentWorkoutRow workout(Long horseId, LocalDate date, Integer maxHr, Integer recoveryHr, Integer rating) {
        return new RecentWorkoutRow(horseId, date, null, maxHr, recoveryHr, rating);
    }

    // =================================================================
    // Fitness trend
    // =================================================================

    @Test
    @DisplayName("7.1: the fitness trend is read with wide date bounds when from/to are empty")
    void testGetFitnessTrend_Success() {
        Long horseId = 1L;
        when(horseRepository.findById(horseId)).thenReturn(Optional.of(horse(horseId, 5L)));
        List<HorseFitnessTrendItemResponse> rows = List.of(
                new HorseFitnessTrendItemResponse(101L, LocalDate.of(2026, 9, 1), "Gallop", null, null, null, null, 135, null, null, 7),
                new HorseFitnessTrendItemResponse(102L, LocalDate.of(2026, 9, 3), "Gallop", null, null, null, null, 145, null, null, 9));
        when(workoutRepository.findFitnessTrend(eq(horseId), any(LocalDate.class), any(LocalDate.class))).thenReturn(rows);

        List<HorseFitnessTrendItemResponse> result = service.getFitnessTrend(horseId, null, null, manager);

        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2026, 9, 1), result.get(0).getDate());
        assertEquals(9, result.get(1).getPerformanceRating());

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(workoutRepository).findFitnessTrend(eq(horseId), from.capture(), to.capture());
        assertEquals(LocalDate.of(1900, 1, 1), from.getValue());
        assertEquals(LocalDate.of(2999, 12, 31), to.getValue());
    }

    @Test
    @DisplayName("An owner cannot read the fitness trend of someone else's horse")
    void testGetFitnessTrend_OtherOwner_Denied() {
        AuthenticatedUser owner = new AuthenticatedUser(5L, "owner@example.com", "HORSE_OWNER");
        when(horseRepository.findById(1L)).thenReturn(Optional.of(horse(1L, 99L)));

        assertThrows(AccessDeniedException.class, () -> service.getFitnessTrend(1L, null, null, owner));
        verifyNoInteractions(workoutRepository);
    }

    @Test
    @DisplayName("An owner can read the fitness trend of their own horse")
    void testGetFitnessTrend_OwnHorse_Allowed() {
        AuthenticatedUser owner = new AuthenticatedUser(5L, "owner@example.com", "HORSE_OWNER");
        when(horseRepository.findById(1L)).thenReturn(Optional.of(horse(1L, 5L)));
        when(workoutRepository.findFitnessTrend(eq(1L), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        assertTrue(service.getFitnessTrend(1L, null, null, owner).isEmpty());
    }

    @Test
    @DisplayName("A head trainer can only read horses in the areas they manage")
    void testGetFitnessTrend_TrainerOutsideArea_Denied() {
        AuthenticatedUser trainer = new AuthenticatedUser(9L, "trainer@example.com", "HEAD_TRAINER");
        when(horseRepository.findById(1L)).thenReturn(Optional.of(horse(1L, 5L)));
        when(planRepository.existsHorseInTrainerArea(1L, 9L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getFitnessTrend(1L, null, null, trainer));
        verifyNoInteractions(workoutRepository);
    }

    @Test
    @DisplayName("An unknown horse is not found")
    void testGetFitnessTrend_UnknownHorse() {
        when(horseRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getFitnessTrend(404L, null, null, manager));
    }

    // =================================================================
    // Alerts
    // =================================================================

    @Test
    @DisplayName("7.2: raises alerts for max heart rate > 220, recovery > 100, falling performance and repeated incidents")
    void testGetHorseAlerts_MultipleRulesTriggered() {
        Long horseId = 2L;
        LocalDate today = LocalDate.now();
        when(horseRepository.findById(horseId)).thenReturn(Optional.of(horse(horseId, 5L)));

        // Newest first: max HR 225 (>220), recovery 110 (>100), rating falling 8 -> 6 -> 4
        when(workoutRepository.findRecentCompletedWorkouts(eq(List.of(horseId)), any(LocalDate.class)))
                .thenReturn(List.of(
                        workout(horseId, today.minusDays(1), 225, 110, 4),
                        workout(horseId, today.minusDays(3), 200, 90, 6),
                        workout(horseId, today.minusDays(5), 195, 85, 8)));
        // Incidents: 2 within 14 days
        when(incidentReportRepository.countByHorseIdAndReportedAtAfter(eq(horseId), any(LocalDateTime.class)))
                .thenReturn(2L);

        List<HorseAlertResponse> alerts = service.getHorseAlerts(horseId, manager);

        assertEquals(4, alerts.size());
        assertTrue(alerts.stream().anyMatch(a -> "MAX_HEART_RATE_EXCEEDED".equals(a.getRuleCode())),
                "Max heart rate alert expected");
        assertTrue(alerts.stream().anyMatch(a -> "POOR_RECOVERY_HEART_RATE".equals(a.getRuleCode())),
                "Poor recovery alert expected");
        assertTrue(alerts.stream().anyMatch(a -> "CONSECUTIVE_PERFORMANCE_DROP".equals(a.getRuleCode())),
                "Consecutive performance drop alert expected");
        assertTrue(alerts.stream().anyMatch(a -> "REPEATED_INCIDENTS".equals(a.getRuleCode())),
                "Repeated incidents alert expected");
    }

    @Test
    @DisplayName("No alert when every value is inside its safe range")
    void testGetHorseAlerts_NothingTriggered() {
        Long horseId = 2L;
        when(horseRepository.findById(horseId)).thenReturn(Optional.of(horse(horseId, 5L)));
        when(workoutRepository.findRecentCompletedWorkouts(eq(List.of(horseId)), any(LocalDate.class)))
                .thenReturn(List.of(
                        workout(horseId, LocalDate.now().minusDays(1), 200, 90, 8),
                        workout(horseId, LocalDate.now().minusDays(3), 205, 95, 7),
                        workout(horseId, LocalDate.now().minusDays(5), 198, 88, 7)));
        when(incidentReportRepository.countByHorseIdAndReportedAtAfter(eq(horseId), any(LocalDateTime.class)))
                .thenReturn(1L);

        assertTrue(service.getHorseAlerts(horseId, manager).isEmpty());
    }

    @Test
    @DisplayName("The performance-drop alert needs 3 rated workouts that fall strictly")
    void testGetHorseAlerts_PerformanceDropNeedsThreeFallingRatings() {
        Long horseId = 2L;
        when(horseRepository.findById(horseId)).thenReturn(Optional.of(horse(horseId, 5L)));
        // 6 -> 6 -> 8 is not strictly falling; only two ratings in the second case
        when(workoutRepository.findRecentCompletedWorkouts(eq(List.of(horseId)), any(LocalDate.class)))
                .thenReturn(List.of(
                        workout(horseId, LocalDate.now().minusDays(1), 150, 60, 6),
                        workout(horseId, LocalDate.now().minusDays(2), 150, 60, 6),
                        workout(horseId, LocalDate.now().minusDays(3), 150, 60, 8)));
        when(incidentReportRepository.countByHorseIdAndReportedAtAfter(eq(horseId), any(LocalDateTime.class)))
                .thenReturn(0L);

        assertTrue(service.getHorseAlerts(horseId, manager).isEmpty());
    }

    // =================================================================
    // Dashboard
    // =================================================================

    @Test
    @DisplayName("7.3: trainer dashboard with plan progress, ratings and alerts, loaded with a constant number of queries")
    void testGetTrainerDashboard_Success() {
        Long trainerId = 9L;
        AuthenticatedUser trainer = new AuthenticatedUser(trainerId, "trainer@example.com", "HEAD_TRAINER");
        LocalDate today = LocalDate.now();

        when(planRepository.findDashboardHorses(trainerId)).thenReturn(List.of(
                new DashboardHorseRow(50L, "Wind God", "Thoroughbred", "A-01"),
                new DashboardHorseRow(51L, "Idle Horse", "Arabian", "A-02")));
        when(planRepository.findDashboardPlans(trainerId)).thenReturn(List.of(
                new DashboardPlanRow(700L, 50L, TrainingPlanStatus.COMPLETED, today.minusDays(60), today.minusDays(30),
                        "Old course", 10, 10L),
                new DashboardPlanRow(701L, 50L, TrainingPlanStatus.ACTIVE, today.minusDays(5), today.plusDays(20),
                        "Sprint course", 10, 1L)));
        when(workoutRepository.findRecentCompletedWorkouts(eq(List.of(50L, 51L)), any(LocalDate.class)))
                .thenReturn(List.of(
                        workout(50L, today, null, null, 8),
                        workout(50L, today.minusDays(2), null, null, 6)));
        when(incidentReportRepository.countIncidentsByHorseIds(eq(List.of(50L, 51L)), any(LocalDateTime.class)))
                .thenReturn(List.of(new IncidentCountRow(51L, 2L)));

        List<TrainerDashboardHorseResponse> dashboard = service.getTrainerDashboard(trainer);

        assertEquals(2, dashboard.size());

        TrainerDashboardHorseResponse active = dashboard.get(0);
        assertEquals("Wind God", active.getHorseName());
        assertEquals("A-01", active.getStallCode());
        assertEquals("ACTIVE", active.getPlanStatus(), "The ACTIVE plan wins over the COMPLETED one");
        assertEquals("Sprint course", active.getCourseName());
        assertEquals(1, active.getCompletedSessions());
        assertEquals(10, active.getTotalSessions());
        assertEquals(10.0, active.getProgressPercent());
        assertEquals(8, active.getLatestPerformanceRating());
        assertEquals(7.0, active.getAvgPerformanceRating30d());
        assertEquals(0, active.getAlertsCount());

        TrainerDashboardHorseResponse idle = dashboard.get(1);
        assertEquals("NONE", idle.getPlanStatus());
        assertEquals("No plan yet", idle.getCourseName());
        assertEquals(0.0, idle.getProgressPercent());
        assertEquals(1, idle.getAlertsCount(), "2 incidents in 14 days raise the repeated-incidents alert");
        assertEquals(List.of("Repeated health incidents"), idle.getAlertTitles());

        // Cố định 4 truy vấn dù có bao nhiêu ngựa (4 queries no matter how many horses)
        verify(planRepository, times(1)).findDashboardHorses(trainerId);
        verify(planRepository, times(1)).findDashboardPlans(trainerId);
        verify(workoutRepository, times(1)).findRecentCompletedWorkouts(any(), any());
        verify(incidentReportRepository, times(1)).countIncidentsByHorseIds(any(), any());
    }

    @Test
    @DisplayName("A trainer with no horses gets an empty dashboard and no further queries")
    void testGetTrainerDashboard_NoHorses() {
        AuthenticatedUser trainer = new AuthenticatedUser(9L, "trainer@example.com", "HEAD_TRAINER");
        when(planRepository.findDashboardHorses(9L)).thenReturn(List.of());

        assertTrue(service.getTrainerDashboard(trainer).isEmpty());
        verifyNoInteractions(workoutRepository, incidentReportRepository);
        verify(planRepository, never()).findDashboardPlans(any());
    }
}
