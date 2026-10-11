package com.rtms.backend.admission.service;
import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.admission.dto.VetAdmissionQueueItemResponse;
import com.rtms.backend.admission.dto.VetReviewRequest;
import com.rtms.backend.admission.entity.AdmissionApplication;
import com.rtms.backend.admission.entity.CandidateHorseProfile;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.admission.repository.AdmissionDocumentRepository;
import com.rtms.backend.admission.repository.CandidateHorseProfileRepository;
import com.rtms.backend.audit.repository.AuditLogRepository;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.enums.HorseStatus;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.identity.repository.VeterinarianProfileRepository;
import com.rtms.backend.medical.dto.CareScheduleResponse;
import com.rtms.backend.medical.dto.CompleteCareScheduleRequest;
import com.rtms.backend.medical.dto.CreateNextScheduleRequest;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.entity.HealthRecord;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.medical.repository.HorseHealthMetricRepository;
import com.rtms.backend.medical.service.CareScheduleService;
import com.rtms.backend.notification.service.NotificationService;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.training.service.HorseTrainingPlanService;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.config.ApiException;
import com.rtms.backend.event.InitialExamCompletedEvent;
import com.rtms.backend.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionVetReviewWorkflowTest {

    @Mock
    private CareScheduleRepository careScheduleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HorseRepository horseRepository;
    @Mock
    private HealthRecordRepository healthRecordRepository;
    @Mock
    private HorseHealthMetricRepository metricRepository;
    @Mock
    private AdmissionApplicationRepository admissionRepository;
    @Mock
    private StableStallRepository stallRepository;
    @Mock
    private GroomIncidentReportRepository incidentRepository;
    @Mock
    private VeterinarianProfileRepository vetProfileRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EntityManager entityManager;
    @Mock
    private NotificationService notificationService;
    @Mock
    private HorseTrainingPlanService trainingPlanService;
    @Mock
    private CandidateHorseProfileRepository candidateProfileRepository;
    @Mock
    private AdmissionDocumentRepository admissionDocumentRepository;
    @Mock
    private FileStorageService fileStorage;

    private CareScheduleService careScheduleService;
    private AdmissionQueryService admissionQueryService;
    private AdmissionReviewService admissionReviewService;

    @BeforeEach
    void setUp() {
        careScheduleService = new CareScheduleService(
                careScheduleRepository,
                horseRepository,
                healthRecordRepository,
                metricRepository,
                admissionRepository,
                stallRepository,
                userRepository,
                incidentRepository,
                vetProfileRepository,
                auditLogRepository,
                eventPublisher,
                entityManager,
                notificationService,
                new TrainingDecisionService(horseRepository, careScheduleRepository, trainingPlanService)
        );

        admissionQueryService = new AdmissionQueryService(
                admissionRepository,
                candidateProfileRepository,
                admissionDocumentRepository,
                stallRepository,
                healthRecordRepository,
                fileStorage,
                userRepository,
                careScheduleRepository
        );

        admissionReviewService = new AdmissionReviewService(
                admissionRepository,
                careScheduleRepository,
                horseRepository,
                stallRepository,
                careScheduleService,
                healthRecordRepository
        );
    }

    @Test
    @DisplayName("Vet review thiếu kết luận tập luyện -> 400, không đụng tới đơn")
    void reviewByVet_missingTrainingDecision_throws400() {
        VetReviewRequest req = new VetReviewRequest();
        req.setPhysicalExamConfirmed(true);
        req.setFindings("Severe colic");

        ApiException ex = assertThrows(ApiException.class, () ->
                admissionReviewService.reviewByVet(1L, req, 15L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(admissionRepository);
    }

    @Test
    @DisplayName("Khám nhập học BLOCKED: Vet không từ chối đơn — đơn vẫn sang TRAINER_REVIEW kèm kết luận BLOCKED")
    void completeInitialExam_blocked_stillMovesToTrainerReview() {
        Long scheduleId = 100L;
        Long vetId = 15L;
        Long horseId = 20L;
        Long admissionId = 1L;

        CareSchedule schedule = new CareSchedule();
        schedule.setId(scheduleId);
        schedule.setHorseId(horseId);
        schedule.setAdmissionId(admissionId);
        schedule.setCareType(CareType.INITIAL);
        schedule.setStatus(CareScheduleStatus.IN_PROGRESS);
        schedule.setVeterinarianId(vetId);

        Horse horse = new Horse();
        horse.setId(horseId);
        horse.setCurrentStatus(HorseStatus.CANDIDATE);

        AdmissionApplication admission = new AdmissionApplication();
        admission.setId(admissionId);
        admission.setHorseId(horseId);
        admission.setStatus(AdmissionStatus.VET_REVIEW);

        when(careScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(careScheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(admissionRepository.findByIdForUpdate(admissionId)).thenReturn(Optional.of(admission));
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));
        when(horseRepository.findByIdForUpdate(horseId)).thenReturn(Optional.of(horse));
        when(healthRecordRepository.save(any(HealthRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(careScheduleRepository.save(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));
        when(careScheduleRepository.saveAndFlush(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));

        CompleteCareScheduleRequest req = new CompleteCareScheduleRequest();
        req.setFindings("Mild tendon heat");
        req.setDiagnosis("Tendon strain");
        req.setTrainingDecision(TrainingDecision.BLOCKED);
        req.setRestrictionDetails("Box rest 10 days");
        CreateNextScheduleRequest next = new CreateNextScheduleRequest();
        next.setCareType(CareType.ROUTINE);
        next.setDescription("Recheck tendon");
        next.setScheduledDate(LocalDate.now().plusDays(10).toString());
        req.setNextSchedule(next);

        careScheduleService.completeCareSchedule(scheduleId, req, vetId);

        assertEquals(AdmissionStatus.TRAINER_REVIEW, admission.getStatus());
        assertEquals(TrainingDecision.BLOCKED, admission.getVetTrainingDecision());
        assertEquals(TrainingDecision.BLOCKED, horse.getTrainingDecision());
        assertEquals("Box rest 10 days", horse.getTrainingDecisionReason());
        verify(eventPublisher).publishEvent(new InitialExamCompletedEvent(admissionId));
    }

    @Test
    @DisplayName("Atomic complete and create next schedule inside same transaction")
    void completeCareSchedule_atomicNextSchedule_success() {
        Long scheduleId = 100L;
        Long vetId = 15L;
        Long horseId = 20L;
        Long admissionId = 1L;

        CareSchedule schedule = new CareSchedule();
        schedule.setId(scheduleId);
        schedule.setHorseId(horseId);
        schedule.setAdmissionId(admissionId);
        schedule.setCareType(CareType.INITIAL);
        schedule.setStatus(CareScheduleStatus.IN_PROGRESS);
        schedule.setVeterinarianId(vetId);

        Horse horse = new Horse();
        horse.setId(horseId);
        horse.setCurrentStatus(HorseStatus.CANDIDATE);

        AdmissionApplication admission = new AdmissionApplication();
        admission.setId(admissionId);
        admission.setHorseId(horseId);
        admission.setStatus(AdmissionStatus.VET_REVIEW);

        when(careScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(careScheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(admissionRepository.findByIdForUpdate(admissionId)).thenReturn(Optional.of(admission));
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));
        when(horseRepository.findByIdForUpdate(horseId)).thenReturn(Optional.of(horse));
        when(healthRecordRepository.save(any(HealthRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(careScheduleRepository.save(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));
        when(careScheduleRepository.saveAndFlush(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));

        CompleteCareScheduleRequest req = new CompleteCareScheduleRequest();
        req.setFindings("Initial exam clear");
        req.setDiagnosis("Good condition");
        req.setTrainingDecision(TrainingDecision.ALLOWED);

        CreateNextScheduleRequest next = new CreateNextScheduleRequest();
        next.setCareType(CareType.ROUTINE);
        next.setDescription("Follow-up heart rate check");
        next.setScheduledDate(LocalDate.now().plusDays(3).toString());
        req.setNextSchedule(next);

        CareScheduleResponse res = careScheduleService.completeCareSchedule(scheduleId, req, vetId);

        assertNotNull(res);
        assertEquals(CareScheduleStatus.COMPLETED, schedule.getStatus());
        assertEquals(AdmissionStatus.TRAINER_REVIEW, admission.getStatus());
        assertEquals(TrainingDecision.ALLOWED, admission.getVetTrainingDecision());
        assertEquals(15L, admission.getVeterinarianId(), "Vet assignment must be preserved");
        // Gán Trainer chạy sau commit qua sự kiện, không gọi thẳng trong transaction của Vet.
        verify(eventPublisher).publishEvent(new InitialExamCompletedEvent(admissionId));
    }

    @Test
    @DisplayName("Vet Queue is filtered server-side by assigned veterinarian")
    void getVetQueue_filteredByAssignedVet() {
        Long vetId = 15L;
        Long otherVetId = 99L;

        CareSchedule cs = new CareSchedule();
        cs.setId(100L);
        cs.setAdmissionId(1L);
        cs.setHorseId(20L);
        cs.setVeterinarianId(vetId);
        cs.setStatus(CareScheduleStatus.SCHEDULED);
        cs.setCareType(CareType.INITIAL);
        cs.setScheduledAt(LocalDateTime.now().plusDays(1));

        var pageRequest = PageRequest.of(0, 10);
        when(careScheduleRepository.findVetQueuePage(
                eq(vetId), eq(false), eq(""), eq(""), isNull(), isNull(), isNull(), eq(""), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(cs), pageRequest, 1));

        AdmissionApplication adm = new AdmissionApplication();
        adm.setId(1L);
        adm.setOwnerId(5L);
        adm.setHorseId(20L);
        adm.setTrainerId(8L);
        adm.setStatus(AdmissionStatus.VET_REVIEW);
        adm.setSubmittedAt(LocalDateTime.now().minusDays(1));
        when(admissionRepository.findAllById(any())).thenReturn(List.of(adm));

        CandidateHorseProfile cand = new CandidateHorseProfile();
        cand.setAdmissionId(1L);
        cand.setName("Desert Star");
        cand.setBreed("Arabian");
        cand.setDateOfBirth(LocalDate.of(2021, 5, 10));
        when(candidateProfileRepository.findByAdmissionIdIn(any())).thenReturn(List.of(cand));

        User owner = new User();
        owner.setId(5L);
        owner.setFullName("John Owner");

        User trainer = new User();
        trainer.setId(8L);
        trainer.setFullName("Trainer Mike");
        when(userRepository.findAllById(any())).thenReturn(List.of(owner, trainer));

        Page<VetAdmissionQueueItemResponse> queue = admissionQueryService.getVetQueue(
                vetId, null, null, null, null, null, null, pageRequest);

        assertEquals(1, queue.getTotalElements());
        VetAdmissionQueueItemResponse item = queue.getContent().get(0);
        assertEquals(1L, item.admissionId());
        assertEquals("Desert Star", item.candidateName());
        assertEquals("Trainer Mike", item.trainerName());
        assertEquals(CareScheduleStatus.SCHEDULED, item.careSchedule().status());
    }

    @Test
    @DisplayName("assertVetAssignedOrManager throws 403 for unassigned veterinarian")
    void assertVetAssignedOrManager_unassigned_throws403() {
        Long admissionId = 1L;
        Long unauthorizedVetId = 99L;

        when(careScheduleRepository.existsByAdmissionIdAndVeterinarianId(admissionId, unauthorizedVetId)).thenReturn(false);
        when(admissionRepository.findById(admissionId)).thenReturn(Optional.of(new AdmissionApplication()));

        AuthenticatedUser vetUser = new AuthenticatedUser(unauthorizedVetId, "vet@rtms.com", "VETERINARIAN");

        ApiException ex = assertThrows(ApiException.class, () ->
                admissionQueryService.assertVetAssignedOrManager(admissionId, vetUser));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("FORBIDDEN", ex.getErrorCode());
    }
}
