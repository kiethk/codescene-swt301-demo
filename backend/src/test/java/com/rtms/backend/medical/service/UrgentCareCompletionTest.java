package com.rtms.backend.medical.service;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.audit.repository.AuditLogRepository;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.enums.HorseStatus;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.identity.entity.Role;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.identity.repository.VeterinarianProfileRepository;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.entity.HealthRecord;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.medical.repository.HorseHealthMetricRepository;
import com.rtms.backend.notification.service.NotificationService;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.training.service.TrainingWorkoutService;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.config.ApiException;
import com.rtms.backend.medical.dto.CancelCareScheduleRequest;
import com.rtms.backend.medical.dto.CompleteCareScheduleRequest;
import com.rtms.backend.medical.dto.CreateNextScheduleRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrgentCareCompletionTest {

    @Mock private CareScheduleRepository careScheduleRepository;
    @Mock private HorseRepository horseRepository;
    @Mock private HealthRecordRepository healthRecordRepository;
    @Mock private HorseHealthMetricRepository metricRepository;
    @Mock private AdmissionApplicationRepository admissionRepository;
    @Mock private StableStallRepository stallRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroomIncidentReportRepository incidentReportRepository;
    @Mock private VeterinarianProfileRepository veterinarianProfileRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;
    @Mock private NotificationService notificationService;
    @Mock private TrainingWorkoutService workoutService;

    private CareScheduleService careScheduleService;

    private Horse horse;
    private User vet;
    private CareSchedule urgentSchedule;
    private GroomIncidentReport incident;

    @BeforeEach
    void setUp() {
        // TrainingDecisionService thật: test kiểm tra luôn việc chặn tập có hủy buổi tập hay không.
        careScheduleService = new CareScheduleService(
                careScheduleRepository, horseRepository, healthRecordRepository, metricRepository,
                admissionRepository, stallRepository, userRepository, incidentReportRepository,
                veterinarianProfileRepository, auditLogRepository, eventPublisher, entityManager,
                notificationService,
                new TrainingDecisionService(horseRepository, careScheduleRepository, workoutService));

        horse = new Horse();
        horse.setId(10L);
        horse.setName("Thunder");
        horse.setCurrentStatus(HorseStatus.ELIGIBLE);
        horse.setTrainingDecision(TrainingDecision.ALLOWED);

        vet = new User();
        vet.setId(20L);
        vet.setFullName("Dr. Smith");
        vet.setEmail("dr_smith@example.com");
        vet.setActive(true);
        Role role = new Role();
        role.setName("VETERINARIAN");
        vet.setRole(role);

        incident = new GroomIncidentReport();
        incident.setId(30L);
        incident.setStatus(IncidentStatus.IN_REVIEW);

        urgentSchedule = new CareSchedule();
        urgentSchedule.setId(100L);
        urgentSchedule.setHorseId(horse.getId());
        urgentSchedule.setVeterinarianId(vet.getId());
        urgentSchedule.setCareType(CareType.URGENT);
        urgentSchedule.setStatus(CareScheduleStatus.SCHEDULED);
        urgentSchedule.setSourceIncidentId(incident.getId());
    }

    @Test
    @DisplayName("createSchedule URGENT: chặn tập, ghi lý do mặc định, hủy buổi tập tương lai")
    void testCreateUrgentSchedule_blocksAndCancelsTraining() {
        when(horseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(horse));
        when(careScheduleRepository.save(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));

        careScheduleService.createSchedule(10L, CareType.URGENT, null, LocalDateTime.now(), 30L);

        assertEquals(TrainingDecision.BLOCKED, horse.getTrainingDecision());
        assertEquals("Urgent veterinary care pending", horse.getTrainingDecisionReason());
        assertFalse(horse.canTrain());
        verify(workoutService).cancelFutureTrainingForHorse(eq(10L), anyString());
    }

    @Test
    @DisplayName("startCareSchedule chuyển SCHEDULED -> IN_PROGRESS")
    void testStartCareSchedule_success() {
        when(userRepository.findById(vet.getId())).thenReturn(Optional.of(vet));
        when(careScheduleRepository.findById(urgentSchedule.getId())).thenReturn(Optional.of(urgentSchedule));
        when(horseRepository.findByIdForUpdate(horse.getId())).thenReturn(Optional.of(horse));
        when(careScheduleRepository.findByIdForUpdate(urgentSchedule.getId())).thenReturn(Optional.of(urgentSchedule));
        when(careScheduleRepository.save(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));

        var response = careScheduleService.startCareSchedule(urgentSchedule.getId(), vet.getId());

        assertEquals(CareScheduleStatus.IN_PROGRESS, response.status());
        assertEquals(CareScheduleStatus.IN_PROGRESS, urgentSchedule.getStatus());
    }

    @Test
    @DisplayName("Khám xong kết luận ALLOWED: mở chặn tập, xóa lý do, đóng sự cố")
    void testCompleteUrgentSchedule_allowed_releasesBlock() {
        urgentSchedule.setStatus(CareScheduleStatus.IN_PROGRESS);
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        horse.setTrainingDecisionReason("Urgent veterinary care pending");

        stubCompletion();
        when(incidentReportRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        var response = careScheduleService.completeCareSchedule(urgentSchedule.getId(),
                completeRequest(TrainingDecision.ALLOWED), vet.getId());

        assertEquals(CareScheduleStatus.COMPLETED, response.status());
        assertEquals(TrainingDecision.ALLOWED, horse.getTrainingDecision());
        assertNull(horse.getTrainingDecisionReason());
        assertTrue(horse.canTrain());
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        verify(healthRecordRepository).save(any(HealthRecord.class));
        verify(workoutService, never()).cancelFutureTrainingForHorse(anyLong(), anyString());
    }

    @Test
    @DisplayName("Kết luận BLOCKED mà không có lịch khám lại -> 400 FOLLOW_UP_REQUIRED, không ghi gì")
    void testCompleteUrgentSchedule_blockedWithoutFollowUp_rejected() {
        CompleteCareScheduleRequest req = completeRequest(TrainingDecision.BLOCKED);
        req.setRestrictionDetails("Walk only for 5 days");

        ApiException ex = assertThrows(ApiException.class,
                () -> careScheduleService.completeCareSchedule(urgentSchedule.getId(), req, vet.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("FOLLOW_UP_REQUIRED", ex.getErrorCode());
        verifyNoInteractions(healthRecordRepository, workoutService);
    }

    @Test
    @DisplayName("Kết luận BLOCKED + lịch khám lại: chặn tập, hủy buổi tập, tạo lịch ROUTINE nối với ca vừa khám")
    void testCompleteUrgentSchedule_blockedWithFollowUp_blocksAndSchedulesRecheck() {
        urgentSchedule.setStatus(CareScheduleStatus.IN_PROGRESS);
        stubCompletion();
        when(careScheduleRepository.saveAndFlush(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));

        LocalDateTime recheckAt = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0);
        CreateNextScheduleRequest next = new CreateNextScheduleRequest();
        next.setHorseId(horse.getId());
        next.setCareType(CareType.ROUTINE);
        next.setScheduledAt(recheckAt);
        next.setDescription("Khám lại gân chân trước phải");

        CompleteCareScheduleRequest req = completeRequest(TrainingDecision.BLOCKED);
        req.setRestrictionDetails("  Walk only for 5 days  ");
        req.setNextSchedule(next);

        careScheduleService.completeCareSchedule(urgentSchedule.getId(), req, vet.getId());

        assertEquals(TrainingDecision.BLOCKED, horse.getTrainingDecision());
        assertEquals("Walk only for 5 days", horse.getTrainingDecisionReason());
        verify(workoutService).cancelFutureTrainingForHorse(eq(10L), contains("Walk only for 5 days"));

        ArgumentCaptor<CareSchedule> followUp = ArgumentCaptor.forClass(CareSchedule.class);
        verify(careScheduleRepository).saveAndFlush(followUp.capture());
        assertEquals(CareType.ROUTINE, followUp.getValue().getCareType());
        assertEquals(100L, followUp.getValue().getSourceScheduleId());
        assertEquals(recheckAt, followUp.getValue().getRequestedAt());
    }

    @Test
    @DisplayName("preservedUrgentLock: còn ca khẩn cấp khác chưa khám thì kết luận ALLOWED không mở chặn")
    void testCompleteUrgentSchedule_anotherUrgentPending_keepsBlock() {
        urgentSchedule.setStatus(CareScheduleStatus.IN_PROGRESS);
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        horse.setTrainingDecisionReason("Urgent veterinary care pending");

        stubCompletion();
        when(careScheduleRepository.existsByHorseIdAndCareTypeAndStatusInAndIdNot(
                eq(10L), eq(CareType.URGENT), anyList(), eq(100L))).thenReturn(true);

        careScheduleService.completeCareSchedule(urgentSchedule.getId(),
                completeRequest(TrainingDecision.ALLOWED), vet.getId());

        assertEquals(TrainingDecision.BLOCKED, horse.getTrainingDecision());
        assertEquals("Urgent veterinary care pending", horse.getTrainingDecisionReason());
        // Bệnh án vẫn ghi kết luận của lần khám này.
        ArgumentCaptor<HealthRecord> record = ArgumentCaptor.forClass(HealthRecord.class);
        verify(healthRecordRepository).save(record.capture());
        assertEquals(TrainingDecision.ALLOWED, record.getValue().getTrainingDecision());
    }

    @Test
    @DisplayName("Gọi hoàn tất lại bởi đúng Vet là idempotent, không ghi bệnh án lần hai")
    void testCompleteUrgentSchedule_idempotent() {
        urgentSchedule.setStatus(CareScheduleStatus.COMPLETED);

        when(careScheduleRepository.findById(urgentSchedule.getId())).thenReturn(Optional.of(urgentSchedule));
        when(horseRepository.findByIdForUpdate(horse.getId())).thenReturn(Optional.of(horse));
        when(careScheduleRepository.findByIdForUpdate(urgentSchedule.getId())).thenReturn(Optional.of(urgentSchedule));

        var response = careScheduleService.completeCareSchedule(urgentSchedule.getId(),
                completeRequest(TrainingDecision.ALLOWED), vet.getId());

        assertEquals(CareScheduleStatus.COMPLETED, response.status());
        verify(healthRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không cho Manager hủy lần khám cuối cùng của ngựa đang bị chặn tập (409 LAST_FOLLOW_UP)")
    void testCancel_lastPendingExamOfBlockedHorse_rejected() {
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        CareSchedule recheck = new CareSchedule();
        recheck.setId(200L);
        recheck.setHorseId(horse.getId());
        recheck.setCareType(CareType.ROUTINE);
        recheck.setStatus(CareScheduleStatus.SCHEDULED);

        when(careScheduleRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(recheck));
        when(userRepository.findById(1L)).thenReturn(Optional.of(manager()));
        when(horseRepository.findById(horse.getId())).thenReturn(Optional.of(horse));
        when(careScheduleRepository.existsByHorseIdAndStatusInAndIdNot(eq(10L), anyList(), eq(200L)))
                .thenReturn(false);

        CancelCareScheduleRequest req = new CancelCareScheduleRequest();
        req.setReason("Vet on leave");

        ApiException ex = assertThrows(ApiException.class,
                () -> careScheduleService.cancelCareSchedule(200L, req, 1L));

        assertEquals("LAST_FOLLOW_UP", ex.getErrorCode());
        assertEquals(CareScheduleStatus.SCHEDULED, recheck.getStatus());
        verify(careScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy được nếu ngựa bị chặn nhưng còn lần khám khác đang chờ")
    void testCancel_blockedHorseWithAnotherPendingExam_allowed() {
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        CareSchedule recheck = new CareSchedule();
        recheck.setId(200L);
        recheck.setHorseId(horse.getId());
        recheck.setCareType(CareType.ROUTINE);
        recheck.setStatus(CareScheduleStatus.SCHEDULED);

        when(careScheduleRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(recheck));
        when(userRepository.findById(1L)).thenReturn(Optional.of(manager()));
        when(horseRepository.findById(horse.getId())).thenReturn(Optional.of(horse));
        when(careScheduleRepository.existsByHorseIdAndStatusInAndIdNot(eq(10L), anyList(), eq(200L)))
                .thenReturn(true);

        CancelCareScheduleRequest req = new CancelCareScheduleRequest();
        req.setReason("Duplicate booking");

        careScheduleService.cancelCareSchedule(200L, req, 1L);

        assertEquals(CareScheduleStatus.CANCELLED, recheck.getStatus());
    }

    private void stubCompletion() {
        when(careScheduleRepository.findById(urgentSchedule.getId())).thenReturn(Optional.of(urgentSchedule));
        when(horseRepository.findByIdForUpdate(horse.getId())).thenReturn(Optional.of(horse));
        when(careScheduleRepository.findByIdForUpdate(urgentSchedule.getId())).thenReturn(Optional.of(urgentSchedule));
        when(healthRecordRepository.save(any(HealthRecord.class))).thenAnswer(i -> {
            HealthRecord hr = i.getArgument(0);
            hr.setId(999L);
            return hr;
        });
        when(careScheduleRepository.save(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CompleteCareScheduleRequest completeRequest(TrainingDecision decision) {
        CompleteCareScheduleRequest req = new CompleteCareScheduleRequest();
        req.setFindings("Examined");
        req.setDiagnosis("Diagnosis");
        req.setTrainingDecision(decision);
        return req;
    }

    private User manager() {
        Role role = new Role();
        role.setName("CLUB_MANAGER");
        User manager = new User();
        manager.setId(1L);
        manager.setRole(role);
        return manager;
    }
}
