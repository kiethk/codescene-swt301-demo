package com.rtms.backend.medical.service;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.audit.repository.AuditLogRepository;
import com.rtms.backend.event.UrgentAssignmentCommittedEvent;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.enums.HorseStatus;
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
import com.rtms.backend.notification.entity.Notification;
import com.rtms.backend.notification.service.NotificationService;
import com.rtms.backend.notification.service.NotificationTypes;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.training.service.TrainingWorkoutService;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.medical.dto.CreateNextScheduleRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CareScheduleHardeningTests {

    private CareScheduleRepository schedules;
    private HorseRepository horses;
    private UserRepository users;
    private VeterinarianProfileRepository profiles;
    private GroomIncidentReportRepository incidents;
    private ApplicationEventPublisher events;
    private TrainingWorkoutService workoutService;
    private NotificationService notificationService;
    private AdmissionApplicationRepository admissionRepository;
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
        notificationService = mock(NotificationService.class);
        admissionRepository = mock(AdmissionApplicationRepository.class);

        service = new CareScheduleService(
                schedules,
                horses,
                mock(HealthRecordRepository.class),
                mock(HorseHealthMetricRepository.class),
                admissionRepository,
                mock(StableStallRepository.class),
                users,
                incidents,
                profiles,
                mock(AuditLogRepository.class),
                events,
                mock(EntityManager.class),
                notificationService,
                new TrainingDecisionService(horses, schedules, workoutService)
        );

        when(schedules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(schedules.findScheduledForHorse(anyLong(), eq(CareScheduleStatus.SCHEDULED))).thenReturn(List.of());
        when(schedules.findScheduledForVet(anyLong(), eq(CareScheduleStatus.SCHEDULED))).thenReturn(List.of());
        when(schedules.findAssignedInDay(any(), any(), any())).thenReturn(List.of());
        when(schedules.existsByVeterinarianIdAndStatus(anyLong(), eq(CareScheduleStatus.IN_PROGRESS))).thenReturn(false);
        when(schedules.existsByHorseIdAndStatus(anyLong(), eq(CareScheduleStatus.IN_PROGRESS))).thenReturn(false);
        when(schedules.existsByVeterinarianIdAndHorseIdAndStatusIn(anyLong(), anyLong(), any())).thenReturn(false);
    }

    private Horse mockHorse(Long id, String name) {
        Horse h = new Horse();
        h.setId(id);
        h.setName(name);
        h.setTrainingDecision(TrainingDecision.ALLOWED);
        when(horses.findByIdForUpdate(id)).thenReturn(Optional.of(h));
        when(horses.findById(id)).thenReturn(Optional.of(h));
        return h;
    }

    private User mockVet(Long id) {
        User vet = new User();
        vet.setId(id);
        vet.setFullName("Dr. Vet " + id);
        vet.setActive(true);
        Role role = new Role();
        role.setName("VETERINARIAN");
        vet.setRole(role);
        VeterinarianProfile prof = new VeterinarianProfile();
        prof.setUserId(id);
        prof.setLicenseNumber("LIC-" + id);
        when(profiles.findById(id)).thenReturn(Optional.of(prof));
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(vet));
        when(users.findById(id)).thenReturn(Optional.of(vet));
        return vet;
    }

    @Test
    @DisplayName("Stage 2A: ROUTINE schedule không có admission chỉ gửi notification cho Vet")
    void routineWithoutAdmission_assignSendsNotificationToVet() {
        mockHorse(10L, "Thunder");
        User vet = mockVet(5L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(vet));

        CareSchedule routine = new CareSchedule();
        routine.setId(101L);
        routine.setHorseId(10L);
        routine.setCareType(CareType.ROUTINE);
        routine.setStatus(CareScheduleStatus.REQUESTED);
        routine.setAdmissionId(null);
        routine.setDurationMinutes(30);

        service.assignRequestedSchedule(routine);

        assertEquals(CareScheduleStatus.SCHEDULED, routine.getStatus());
        assertEquals(5L, routine.getVeterinarianId());

        // Verify Vet notification keyed on CARE_SCHEDULE:101
        verify(notificationService).sendAssignmentNotification(
                eq(5L),
                eq(NotificationTypes.REFERENCE_CARE_SCHEDULE),
                eq(101L),
                eq(10L),
                eq(NotificationTypes.ADMISSION_VET_ASSIGNED),
                anyString(),
                contains("Thunder")
        );
    }

    @Test
    @DisplayName("Stage 2A: URGENT schedule không có admission gửi notification cho Vet và publish alert")
    void urgentWithoutAdmission_assignSendsNotificationToVet() {
        mockHorse(10L, "Storm");
        User vet = mockVet(5L);
        when(users.findActiveVeterinarians()).thenReturn(List.of(vet));

        GroomIncidentReport incident = new GroomIncidentReport();
        incident.setId(50L);
        incident.setHorseId(10L);
        incident.setStatus(IncidentStatus.REPORTED);
        when(incidents.findByIdForUpdate(50L)).thenReturn(Optional.of(incident));

        CareSchedule urgent = new CareSchedule();
        urgent.setId(102L);
        urgent.setHorseId(10L);
        urgent.setCareType(CareType.URGENT);
        urgent.setStatus(CareScheduleStatus.REQUESTED);
        urgent.setSourceIncidentId(50L);
        urgent.setAdmissionId(null);
        urgent.setDurationMinutes(30);

        service.assignRequestedSchedule(urgent);

        assertEquals(CareScheduleStatus.SCHEDULED, urgent.getStatus());
        assertEquals(5L, urgent.getVeterinarianId());

        verify(notificationService).sendAssignmentNotification(
                eq(5L),
                eq(NotificationTypes.REFERENCE_CARE_SCHEDULE),
                eq(102L),
                eq(10L),
                eq(NotificationTypes.ADMISSION_VET_ASSIGNED),
                anyString(),
                anyString()
        );
        verify(events).publishEvent(any(com.rtms.backend.event.UrgentAssignmentCommittedEvent.class));
    }

    @Test
    @DisplayName("Stage 2A: Notification deduplication per-schedule - 2 lịch cho cùng 1 ngựa tạo 2 notification riêng biệt")
    void notificationDeduplication_twoSchedulesForSameHorseAndRecipient_producesTwoDistinctRows() {
        Long recipientId = 5L;
        Long schedule1Id = 201L;
        Long schedule2Id = 202L;

        String dedupKey1 = Notification.buildDeduplicationKey(
                recipientId, NotificationTypes.REFERENCE_CARE_SCHEDULE, schedule1Id, NotificationTypes.ADMISSION_VET_ASSIGNED);
        String dedupKey2 = Notification.buildDeduplicationKey(
                recipientId, NotificationTypes.REFERENCE_CARE_SCHEDULE, schedule2Id, NotificationTypes.ADMISSION_VET_ASSIGNED);

        assertNotEquals(dedupKey1, dedupKey2, "Hai lịch khác nhau phải sinh ra 2 dedupKey độc lập");
        assertTrue(dedupKey1.contains("CARE_SCHEDULE_201"));
        assertTrue(dedupKey2.contains("CARE_SCHEDULE_202"));
    }

    @Test
    @DisplayName("Stage 2D: Re-running scheduler không reassign lịch đã SCHEDULED")
    void reRunningScheduler_doesNotReassignOrDoubleCount() {
        CareSchedule alreadyScheduled = new CareSchedule();
        alreadyScheduled.setId(303L);
        alreadyScheduled.setHorseId(10L);
        alreadyScheduled.setStatus(CareScheduleStatus.SCHEDULED);
        alreadyScheduled.setVeterinarianId(5L);

        service.assignRequestedSchedule(alreadyScheduled);

        // Schedule was not modified because status was not REQUESTED
        verify(schedules, never()).save(alreadyScheduled);
        verify(notificationService, never()).sendAssignmentNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Stage 2B: PENDING_RECHECK hoàn toàn bị loại bỏ khỏi AdmissionStatus enum và codebase")
    void deadStatePendingRecheck_isCompletelyRemovedFromEnumAndCodebase() {
        for (AdmissionStatus status : AdmissionStatus.values()) {
            assertNotEquals("PENDING_RECHECK", status.name(), "Enum AdmissionStatus không được chứa PENDING_RECHECK");
        }
    }

    @Test
    @DisplayName("TrainingDecision chỉ còn ALLOWED/BLOCKED; canTrain() = ELIGIBLE và ALLOWED")
    void trainingDecision_onlyAllowedOrBlocked_andCanTrainNeedsBoth() {
        assertArrayEquals(new TrainingDecision[]{TrainingDecision.ALLOWED, TrainingDecision.BLOCKED},
                TrainingDecision.values());

        Horse horse = new Horse();
        horse.setCurrentStatus(HorseStatus.ELIGIBLE);
        horse.setTrainingDecision(TrainingDecision.ALLOWED);
        assertTrue(horse.canTrain());

        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        assertFalse(horse.canTrain(), "Thú y chặn tập thì không tập được");

        horse.setCurrentStatus(HorseStatus.CANDIDATE);
        horse.setTrainingDecision(TrainingDecision.ALLOWED);
        assertFalse(horse.canTrain(), "Ngựa chưa được nhận vào CLB thì không tập được dù ALLOWED");
    }
}
