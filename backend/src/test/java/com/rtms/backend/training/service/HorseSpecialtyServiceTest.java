package com.rtms.backend.training.service;

import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.DistanceStat;
import com.rtms.backend.training.dto.HorseSpecialtyResponse;
import com.rtms.backend.training.dto.SpecialtyWorkoutRow;
import com.rtms.backend.training.enums.DistanceBand;
import com.rtms.backend.training.enums.SurfaceType;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HorseSpecialtyServiceTest {

    @Mock
    private TrainingWorkoutRepository workoutRepository;

    @Mock
    private HorseViewAccessService accessService;

    private HorseSpecialtyService service;

    private final AuthenticatedUser trainer = new AuthenticatedUser(9L, "trainer@example.com", "HEAD_TRAINER");

    @BeforeEach
    void setUp() {
        service = new HorseSpecialtyService(workoutRepository, accessService);
    }

    private SpecialtyWorkoutRow run(long meters, SurfaceType surface, int rating) {
        return new SpecialtyWorkoutRow(1L, BigDecimal.valueOf(meters), surface, rating, null);
    }

    private SpecialtyWorkoutRow run(long meters, SurfaceType surface, int rating, double topSpeed) {
        return new SpecialtyWorkoutRow(1L, BigDecimal.valueOf(meters), surface, rating, BigDecimal.valueOf(topSpeed));
    }

    private DistanceStat statOf(HorseSpecialtyResponse response, DistanceBand band) {
        return response.distanceStats().stream().filter(s -> s.band() == band).findFirst().orElseThrow();
    }

    // =================================================================
    // Nhóm cự ly (Distance bands)
    // =================================================================

    @Test
    @DisplayName("Distance bands: each limit belongs to the lower band")
    void distanceBandLimits() {
        assertEquals(DistanceBand.SPRINT, DistanceBand.of(BigDecimal.valueOf(500)));
        assertEquals(DistanceBand.SPRINT, DistanceBand.of(BigDecimal.valueOf(1400)));
        assertEquals(DistanceBand.MILE, DistanceBand.of(new BigDecimal("1400.5")));
        assertEquals(DistanceBand.MILE, DistanceBand.of(BigDecimal.valueOf(1800)));
        assertEquals(DistanceBand.MIDDLE, DistanceBand.of(BigDecimal.valueOf(2400)));
        assertEquals(DistanceBand.STAYER, DistanceBand.of(BigDecimal.valueOf(2401)));
        assertEquals(DistanceBand.STAYER, DistanceBand.of(BigDecimal.valueOf(3200)));
    }

    // =================================================================
    // Đủ dữ liệu (Sufficient data)
    // =================================================================

    @Test
    @DisplayName("No workouts: not enough data, nothing assigned")
    void noWorkouts_isInsufficient() {
        HorseSpecialtyResponse response = service.evaluate(List.of());
        assertEquals(3, response.minWorkoutsPerGroup());
        assertTrue(response.strongDistances().isEmpty());
        assertNull(response.preferredSurface());
        assertTrue(response.distanceStats().isEmpty());
    }

    @Test
    @DisplayName("Two good workouts in a band are not enough: no badge, but the stats are still reported")
    void twoWorkouts_isInsufficient_butStatsAreKept() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 9), run(1200, SurfaceType.TURF, 9)));
        assertTrue(response.strongDistances().isEmpty());
        assertEquals(2, statOf(response, DistanceBand.SPRINT).workoutCount());
    }

    @Test
    @DisplayName("Enough workouts spread over bands but none reaching the minimum: insufficient")
    void workoutsSpreadThin_isInsufficient() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 9), run(1200, SurfaceType.TURF, 9),
                run(1600, SurfaceType.TURF, 9), run(1700, SurfaceType.TURF, 9),
                run(2000, SurfaceType.TURF, 9), run(2200, SurfaceType.TURF, 9)));
        assertTrue(response.strongDistances().isEmpty());
        assertNull(response.preferredSurface(), "Nothing is assigned when no band is judged, surface included");
    }

    // =================================================================
    // Huy hiệu cự ly (Distance badges)
    // =================================================================

    @Test
    @DisplayName("Three good sprint workouts earn the SPRINT badge")
    void goodSprintWorkouts_earnSprint() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 8, 62.0), run(1200, SurfaceType.TURF, 9, 60.0),
                run(1200, SurfaceType.TURF, 8, 58.0), run(800, SurfaceType.TURF, 7, 57.0)));
        assertEquals(List.of(DistanceBand.SPRINT), response.strongDistances());
        DistanceStat sprint = statOf(response, DistanceBand.SPRINT);
        assertEquals(4, sprint.workoutCount());
        assertEquals(new BigDecimal("8.0"), sprint.averageRating());
        assertEquals(new BigDecimal("59.3"), sprint.averageTopSpeedKmh());
    }

    @Test
    @DisplayName("A band with enough workouts but a low average rating earns no badge")
    void lowRating_earnsNoBadge() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 5), run(1200, SurfaceType.TURF, 6), run(1100, SurfaceType.TURF, 6)));
        assertTrue(response.strongDistances().isEmpty());
    }

    @Test
    @DisplayName("The threshold is checked on the rounded average that the user sees")
    void thresholdUsesRoundedAverage() {
        // 7 + 7 + 7 + 7 + 7 + 7 + 8 = 50 / 7 = 7.14 -> shown as 7.1, which passes
        HorseSpecialtyResponse pass = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 7), run(1000, SurfaceType.TURF, 7), run(1000, SurfaceType.TURF, 7),
                run(1000, SurfaceType.TURF, 7), run(1000, SurfaceType.TURF, 7), run(1000, SurfaceType.TURF, 7),
                run(1000, SurfaceType.TURF, 8)));
        assertEquals(List.of(DistanceBand.SPRINT), pass.strongDistances());

        // 7 + 7 + 6 = 20 / 3 = 6.7 -> below the threshold
        HorseSpecialtyResponse fail = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 7), run(1000, SurfaceType.TURF, 7), run(1000, SurfaceType.TURF, 6)));
        assertTrue(fail.strongDistances().isEmpty());
    }

    @Test
    @DisplayName("A versatile horse earns several badges, shortest distance first")
    void versatileHorse_earnsSeveralBadges() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1600, SurfaceType.TURF, 8), run(1600, SurfaceType.TURF, 8), run(1700, SurfaceType.TURF, 9),
                run(1000, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 9), run(1200, SurfaceType.TURF, 8)));

        assertEquals(List.of(DistanceBand.SPRINT, DistanceBand.MILE), response.strongDistances());
    }

    @Test
    @DisplayName("Top speed is only evidence: a missing speed gives a null average and never blocks a badge")
    void missingTopSpeed_doesNotBlockBadge() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 8)));

        assertEquals(List.of(DistanceBand.SPRINT), response.strongDistances());
        assertNull(statOf(response, DistanceBand.SPRINT).averageTopSpeedKmh());
    }

    // =================================================================
    // Mặt sân (Surface)
    // =================================================================

    @Test
    @DisplayName("A surface is preferred when it leads by at least one point and reaches the minimum rating")
    void clearSurfaceLead_isPreferred() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 9), run(1200, SurfaceType.TURF, 9), run(1100, SurfaceType.TURF, 9),
                run(1000, SurfaceType.DIRT, 7), run(1200, SurfaceType.DIRT, 7), run(1100, SurfaceType.DIRT, 7)));

        assertEquals(SurfaceType.TURF, response.preferredSurface());
    }

    @Test
    @DisplayName("A lead of exactly one point counts, even with a decimal average")
    void leadOfExactlyOnePoint_counts() {
        // TURF: 9+8+8 = 25/3 = 8.3 ; DIRT: 8+7+7 = 22/3 = 7.3 ; lead is exactly 1.0
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 9), run(1000, SurfaceType.TURF, 8), run(1000, SurfaceType.TURF, 8),
                run(1000, SurfaceType.DIRT, 8), run(1000, SurfaceType.DIRT, 7), run(1000, SurfaceType.DIRT, 7)));

        assertEquals(SurfaceType.TURF, response.preferredSurface());
    }

    @Test
    @DisplayName("A lead under one point is noise: no preferred surface")
    void smallSurfaceLead_isNotPreferred() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 8), run(1000, SurfaceType.TURF, 8), run(1000, SurfaceType.TURF, 9),
                run(1000, SurfaceType.DIRT, 8), run(1000, SurfaceType.DIRT, 8), run(1000, SurfaceType.DIRT, 8)));

        assertNull(response.preferredSurface());
    }

    @Test
    @DisplayName("A surface the horse has only run on is never concluded as preferred")
    void singleSurface_isNotPreferred() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 9), run(1000, SurfaceType.TURF, 9), run(1000, SurfaceType.TURF, 9),
                run(1000, SurfaceType.DIRT, 4), run(1000, SurfaceType.DIRT, 4)));
        assertNull(response.preferredSurface(), "DIRT has only 2 workouts, so there is nothing to compare TURF with");
    }

    @Test
    @DisplayName("The best surface must itself reach the minimum rating")
    void weakBestSurface_isNotPreferred() {
        HorseSpecialtyResponse response = service.evaluate(List.of(
                run(1000, SurfaceType.TURF, 6), run(1000, SurfaceType.TURF, 6), run(1000, SurfaceType.TURF, 6),
                run(1000, SurfaceType.DIRT, 4), run(1000, SurfaceType.DIRT, 4), run(1000, SurfaceType.DIRT, 4)));

        assertNull(response.preferredSurface());
    }

    // =================================================================
    // Truy vấn và phân quyền (Loading and authorization)
    // =================================================================

    @Test
    @DisplayName("Many horses are loaded with one query and every horse gets an entry, even without workouts")
    void getSpecialties_oneQuery_everyHorseHasAnEntry() {
        when(workoutRepository.findSpecialtyWorkouts(eq(List.of(1L, 2L)), any(LocalDate.class))).thenReturn(List.of(
                run(1000, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 8)));

        Map<Long, HorseSpecialtyResponse> result = service.getSpecialties(List.of(1L, 2L));

        assertEquals(List.of(DistanceBand.SPRINT), result.get(1L).strongDistances());
        assertTrue(result.get(2L).strongDistances().isEmpty(), "A horse without workouts gets no badge");
        assertTrue(result.get(2L).distanceStats().isEmpty());
        verify(workoutRepository, times(1)).findSpecialtyWorkouts(any(), any());
    }

    @Test
    @DisplayName("An empty horse list does not hit the database")
    void getSpecialties_emptyList_noQuery() {
        assertTrue(service.getSpecialties(List.of()).isEmpty());
        verifyNoInteractions(workoutRepository);
    }

    @Test
    @DisplayName("getSpecialty checks the view permission before loading anything")
    void getSpecialty_checksAccessFirst() {
        doThrow(new AccessDeniedException("no access")).when(accessService).assertCanViewHorse(5L, trainer);

        assertThrows(AccessDeniedException.class, () -> service.getSpecialty(5L, trainer));
        verifyNoInteractions(workoutRepository);
    }

    @Test
    @DisplayName("getSpecialty returns the specialty of the requested horse")
    void getSpecialty_success() {
        when(workoutRepository.findSpecialtyWorkouts(eq(List.of(1L)), any(LocalDate.class))).thenReturn(List.of(
                run(1000, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 8), run(1200, SurfaceType.TURF, 8)));

        HorseSpecialtyResponse response = service.getSpecialty(1L, trainer);

        assertEquals(List.of(DistanceBand.SPRINT), response.strongDistances());
        verify(accessService).assertCanViewHorse(1L, trainer);
    }
}
