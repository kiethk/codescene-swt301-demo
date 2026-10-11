package com.rtms.backend.medical.service;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.audit.repository.AuditLogRepository;
import com.rtms.backend.event.UrgentAssignmentCommittedEvent;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.identity.entity.Role;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.identity.entity.VeterinarianProfile;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.identity.repository.VeterinarianProfileRepository;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.medical.repository.HorseHealthMetricRepository;
import com.rtms.backend.notification.service.NotificationService;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.enums.IncidentSeverity;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.training.service.TrainingWorkoutService;
import com.rtms.backend.training.service.TrainingDecisionService;


import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CareScheduleDirectAssignmentTest {

    private CareScheduleRepository schedules;
    private HorseRepository horses;
    private UserRepository users;
    private VeterinarianProfileRepository profiles;
    private GroomIncidentReportRepository incidents;
    private ApplicationEventPublisher events;
    private TrainingWorkoutService workoutService;
    private CareScheduleService service;

    @BeforeEach
    void setUp() {
        schedules = mock(CareScheduleRepository.class);
        horses = mock(HorseRepository.class);
        users = mock(UserRepository.class);
        profiles = mock(VeterinarianProfileRepository.class);
        incidents = mock(GroomIncidentReportRepository.class);
        events = mock(ApplicationEventPublisher.class);
        workoutService = mock(TrainingWorkoutService.class);
        service = new CareScheduleService(
                schedules,
                horses,
                mock(HealthRecordRepository.class),
                mock(HorseHealthMetricRepository.class),
                mock(AdmissionApplicationRepository.class),
                mock(StableStallRepository.class),
                users,
                incidents,
                profiles,
                mock(AuditLogRepository.class),
                events,
                mock(EntityManager.class),
                mock(NotificationService.class),
                new TrainingDecisionService(horses, schedules, workoutService));
        when(schedules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(schedules.findScheduledForHorse(anyLong(), eq(CareScheduleStatus.SCHEDULED))).thenReturn(List.of());
        when(schedules.findScheduledForVet(anyLong(), eq(CareScheduleStatus.SCHEDULED))).thenReturn(List.of());
        when(schedules.findAssignedInDay(any(), any(), any())).thenReturn(List.of());
        when(schedules.existsByVeterinarianIdAndStatus(anyLong(), eq(CareScheduleStatus.IN_PROGRESS))).thenReturn(false);
        when(schedules.existsByHorseIdAndStatus(anyLong(), eq(CareScheduleStatus.IN_PROGRESS))).thenReturn(false);
        when(schedules.existsByVeterinarianIdAndHorseIdAndStatusIn(anyLong(), anyLong(), any())).thenReturn(false);
    }

    @Test
    void selectsVetWithFewestDistinctHorsesBeforeTotalMinutes() {
        CareSchedule requested = urgentRequest(99L);
        User twoHorses = eligibleVet(1L);
        User oneHorse = eligibleVet(2L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(twoHorses, oneHorse));
        lockEligible(twoHorses, oneHorse);
        when(schedules.findAssignedInDay(any(), any(), any())).thenReturn(List.of(
                assigned(1L, 10L, 30), assigned(1L, 11L, 30), assigned(2L, 12L, 120)));

        service.assignRequestedSchedule(requested);

        assertEquals(2L, requested.getVeterinarianId());
        assertEquals(CareScheduleStatus.SCHEDULED, requested.getStatus());
    }

    @Test
    void countsDistinctHorseIdsRatherThanScheduleRows() {
        CareSchedule requested = urgentRequest(99L);
        User repeatedHorseVet = eligibleVet(1L);
        User singleScheduleVet = eligibleVet(2L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(repeatedHorseVet, singleScheduleVet));
        lockEligible(repeatedHorseVet, singleScheduleVet);
        when(schedules.findAssignedInDay(any(), any(), any())).thenReturn(List.of(
                assigned(1L, 10L, 10), assigned(1L, 10L, 10), assigned(2L, 12L, 30)));

        service.assignRequestedSchedule(requested);

        assertEquals(1L, requested.getVeterinarianId(),
                "Two rows for the same Horse must count as one Horse; total minutes then breaks the tie");
    }

    @Test
    void excludesVetWithOverlappingScheduledCare() {
        CareSchedule requested = urgentRequest(99L);
        User busy = eligibleVet(1L);
        User free = eligibleVet(2L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(busy, free));
        lockEligible(busy, free);
        CareSchedule overlap = assigned(1L, 10L, 30);
        overlap.setScheduledAt(requested.getRequestedAt());
        when(schedules.findScheduledForVet(1L, CareScheduleStatus.SCHEDULED)).thenReturn(List.of(overlap));

        service.assignRequestedSchedule(requested);

        assertEquals(2L, requested.getVeterinarianId());
    }

    @Test
    void leavesScheduleRequestedWhenNoVetIsEligible() {
        CareSchedule requested = urgentRequest(99L);
        when(users.findActiveVeterinarians()).thenReturn(List.of());

        service.assignRequestedSchedule(requested);

        assertEquals(CareScheduleStatus.REQUESTED, requested.getStatus());
        assertNull(requested.getVeterinarianId());
        assertNull(requested.getScheduledAt());
    }

    @Test
    void excludesVetWhoHasAnInProgressCareSchedule() {
        CareSchedule requested = urgentRequest(99L);
        User occupied = eligibleVet(1L);
        User free = eligibleVet(2L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(occupied, free));
        when(schedules.existsByVeterinarianIdAndStatus(1L, CareScheduleStatus.IN_PROGRESS)).thenReturn(true);
        lockEligible(occupied, free);

        service.assignRequestedSchedule(requested);

        assertEquals(2L, requested.getVeterinarianId());
    }

    @Test
    void urgentAssignmentUpdatesIncidentAndPublishesCommittedEvent() {
        Horse horse = new Horse();
        horse.setId(99L);
        horse.setName("Rocket");
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        when(horses.findByIdForUpdate(99L)).thenReturn(Optional.of(horse));

        CareSchedule requested = new CareSchedule();
        requested.setId(500L);
        requested.setHorseId(99L);
        requested.setCareType(CareType.URGENT);
        requested.setStatus(CareScheduleStatus.REQUESTED);
        requested.setDurationMinutes(30);
        requested.setSourceIncidentId(700L);

        GroomIncidentReport incident = new GroomIncidentReport();
        incident.setId(700L);
        incident.setGroomId(8L);
        incident.setHorseId(99L);
        incident.setTitle("Acute lameness");
        incident.setDescription("Horse cannot bear weight on the left foreleg.");
        incident.setSeverity(IncidentSeverity.CRITICAL);
        incident.setStatus(IncidentStatus.REPORTED);
        incident.setReportedAt(LocalDateTime.now());
        when(incidents.findByIdForUpdate(700L)).thenReturn(Optional.of(incident));

        User vet = eligibleVet(2L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(vet));
        lockEligible(vet);

        service.assignRequestedSchedule(requested);

        assertEquals(CareScheduleStatus.SCHEDULED, requested.getStatus());
        assertEquals(2L, requested.getVeterinarianId());
        assertEquals(IncidentStatus.IN_REVIEW, incident.getStatus());
        assertEquals(2L, incident.getHandledById());
        assertNotNull(incident.getHandledAt());
        verify(incidents).save(incident);
        verify(events).publishEvent(any(com.rtms.backend.event.UrgentAssignmentCommittedEvent.class));
    }

    @Test
    void creatingUrgentScheduleBlocksHorseAndKeepsItRequestedWithoutVet() {
        Horse horse = new Horse();
        horse.setId(99L);
        horse.setName("Rocket");
        horse.setTrainingDecision(TrainingDecision.ALLOWED);
        when(horses.findByIdForUpdate(99L)).thenReturn(Optional.of(horse));
        when(users.findActiveVeterinarians()).thenReturn(List.of());
        when(schedules.save(any(CareSchedule.class))).thenAnswer(invocation -> {
            CareSchedule saved = invocation.getArgument(0);
            saved.setId(501L);
            return saved;
        });

        service.createSchedule(99L, CareType.URGENT, "Acute lameness", null, 700L);

        assertEquals(TrainingDecision.BLOCKED, horse.getTrainingDecision());
        // Chặn tập luôn kéo theo hủy buổi tập tương lai.
        verify(workoutService).cancelFutureTrainingForHorse(eq(99L), anyString());
        verify(horses).save(horse);
        verify(schedules, atLeastOnce()).save(argThat(schedule ->
                schedule.getCareType() == CareType.URGENT
                        && schedule.getStatus() == CareScheduleStatus.REQUESTED
                        && schedule.getVeterinarianId() == null
                        && schedule.getScheduledAt() == null
                        && Long.valueOf(700L).equals(schedule.getSourceIncidentId())));
    }

    private void lockEligible(User... vets) {
        for (User vet : vets) {
            when(users.findByIdForUpdate(vet.getId())).thenReturn(Optional.of(vet));
            VeterinarianProfile profile = new VeterinarianProfile();
            profile.setUserId(vet.getId());
            profile.setLicenseNumber("VET-" + vet.getId());
            when(profiles.findById(vet.getId())).thenReturn(Optional.of(profile));
        }
    }

    private User eligibleVet(Long id) {
        Role role = new Role();
        role.setName("VETERINARIAN");
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private CareSchedule urgentRequest(Long horseId) {
        Horse horse = new Horse();
        horse.setId(horseId);
        horse.setName("Horse " + horseId);
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        when(horses.findByIdForUpdate(horseId)).thenReturn(Optional.of(horse));
        CareSchedule schedule = new CareSchedule();
        schedule.setId(500L);
        schedule.setHorseId(horseId);
        schedule.setCareType(CareType.URGENT);
        schedule.setStatus(CareScheduleStatus.REQUESTED);
        schedule.setDurationMinutes(30);
        // No source incident here: ranking tests stop before incident handling by using ROUTINE behavior below.
        schedule.setCareType(CareType.ROUTINE);
        schedule.setRequestedAt(LocalDate.now().plusDays(1).atTime(13, 30));
        return schedule;
    }

    private CareSchedule assigned(Long veterinarianId, Long horseId, int minutes) {
        CareSchedule schedule = new CareSchedule();
        schedule.setId(veterinarianId * 100 + horseId);
        schedule.setVeterinarianId(veterinarianId);
        schedule.setHorseId(horseId);
        schedule.setCareType(CareType.ROUTINE);
        schedule.setStatus(CareScheduleStatus.SCHEDULED);
        schedule.setDurationMinutes(minutes);
        schedule.setScheduledAt(LocalDateTime.now().plusHours(3));
        return schedule;
    }
}
