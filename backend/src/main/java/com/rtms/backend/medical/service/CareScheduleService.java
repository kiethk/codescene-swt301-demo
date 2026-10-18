package com.rtms.backend.medical.service;
import com.rtms.backend.admission.entity.AdmissionApplication;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.admission.service.TrainerAssignmentTriggers;
import com.rtms.backend.audit.entity.AuditLog;
import com.rtms.backend.audit.repository.AuditLogRepository;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.identity.repository.VeterinarianProfileRepository;
import com.rtms.backend.medical.dto.CancelCareScheduleRequest;
import com.rtms.backend.medical.dto.CareScheduleDetailResponse;
import com.rtms.backend.medical.dto.CareScheduleResponse;
import com.rtms.backend.medical.dto.CompleteCareScheduleRequest;
import com.rtms.backend.medical.dto.CreateNextScheduleRequest;
import com.rtms.backend.medical.dto.HorseHealthMetricRequest;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.entity.HealthRecord;
import com.rtms.backend.medical.entity.HorseHealthMetric;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.medical.repository.HorseHealthMetricRepository;
import com.rtms.backend.notification.dto.UrgentAssignmentAlert;
import com.rtms.backend.notification.service.NotificationService;
import com.rtms.backend.notification.service.NotificationTypes;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.config.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.rtms.backend.event.InitialExamCompletedEvent;
import com.rtms.backend.event.UrgentAssignmentCommittedEvent;

@Service
public class CareScheduleService {

    private static final int DEFAULT_DURATION_MINUTES = 30;
    private static final int ASSIGNMENT_BATCH_SIZE = 25;

    private final CareScheduleRepository careScheduleRepository;
    private final HorseRepository horseRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final HorseHealthMetricRepository metricRepository;
    private final AdmissionApplicationRepository admissionRepository;
    private final StableStallRepository stallRepository;
    private final UserRepository userRepository;
    private final GroomIncidentReportRepository incidentReportRepository;
    private final VeterinarianProfileRepository veterinarianProfileRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final NotificationService notificationService;
    private final TrainingDecisionService trainingDecisionService;

    public CareScheduleService(
            CareScheduleRepository careScheduleRepository,
            HorseRepository horseRepository,
            HealthRecordRepository healthRecordRepository,
            HorseHealthMetricRepository metricRepository,
            AdmissionApplicationRepository admissionRepository,
            StableStallRepository stallRepository,
            UserRepository userRepository,
            GroomIncidentReportRepository incidentReportRepository,
            VeterinarianProfileRepository veterinarianProfileRepository,
            AuditLogRepository auditLogRepository,
            ApplicationEventPublisher eventPublisher,
            EntityManager entityManager,
            NotificationService notificationService,
            TrainingDecisionService trainingDecisionService) {
        this.careScheduleRepository = careScheduleRepository;
        this.horseRepository = horseRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.metricRepository = metricRepository;
        this.admissionRepository = admissionRepository;
        this.stallRepository = stallRepository;
        this.userRepository = userRepository;
        this.incidentReportRepository = incidentReportRepository;
        this.veterinarianProfileRepository = veterinarianProfileRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.notificationService = notificationService;
        this.trainingDecisionService = trainingDecisionService;
    }

    @Transactional
    public CareScheduleResponse createInitialSchedule(Long admissionId, Long horseId) {
        if (admissionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "admissionId is required");
        }
        AdmissionApplication adm = admissionRepository.findByIdForUpdate(admissionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Admission not found"));
        if (adm.getStatus() != AdmissionStatus.VET_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REVIEW_STATE", "Admission is not awaiting a vet examination");
        }
        if (horseId != null && !Objects.equals(horseId, adm.getHorseId())) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_MISMATCH", "Horse does not belong to this admission");
        }
        horseId = adm.getHorseId();
        if (horseId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HORSE_NOT_ASSIGNED", "Admission does not have a horse assigned yet");
        }

        // Khóa hàng ngựa để hai yêu cầu tạo lịch khám song song không cùng lọt qua bước kiểm tra trùng.
        horseRepository.findByIdForUpdate(horseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Horse not found"));
        boolean activeExists = careScheduleRepository.existsByHorseIdAndCareTypeAndStatusIn(
                horseId, CareType.INITIAL,
                List.of(CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS));
        if (activeExists) {
            CareSchedule existing = careScheduleRepository
                    .findFirstByAdmissionIdAndCareTypeOrderByCreatedAtDesc(admissionId, CareType.INITIAL)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SCHEDULE_EXISTS", "Active schedule already exists"));
            return CareScheduleResponse.from(existing);
        }

        CareSchedule schedule = new CareSchedule();
        schedule.setHorseId(horseId);
        schedule.setAdmissionId(admissionId);
        schedule.setCareType(CareType.INITIAL);
        schedule.setStatus(CareScheduleStatus.REQUESTED);
        schedule.setDurationMinutes(DEFAULT_DURATION_MINUTES);
        schedule.setDescription("Initial admission physical examination in quarantine area");
        // Không khóa ngựa ở đây: ngựa CANDIDATE vốn không tập được (Horse.canTrain()),
        // kết luận y tế do chính lần khám này quyết định.
        CareSchedule saved = careScheduleRepository.save(schedule);
        assignRequestedSchedule(saved);
        return CareScheduleResponse.from(saved);
    }

    @Transactional
    public CareScheduleResponse createSchedule(Long horseId, CareType careType, String description, LocalDateTime requestedAt, Long sourceIncidentId) {
        Horse horse = horseRepository.findByIdForUpdate(horseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Horse not found"));

        CareSchedule schedule = new CareSchedule();
        schedule.setHorseId(horseId);
        schedule.setCareType(careType);
        schedule.setStatus(CareScheduleStatus.REQUESTED);
        schedule.setRequestedAt(requestedAt);
        schedule.setDurationMinutes(DEFAULT_DURATION_MINUTES);
        schedule.setDescription(description != null ? description.trim() : null);
        schedule.setSourceIncidentId(sourceIncidentId);

        if (careType == CareType.URGENT) {
            // Chặn phòng ngừa ngay khi có ca khẩn cấp, kéo theo hủy buổi tập
            // tương lai: không để ngựa đang có vấn đề vẫn được dắt ra sân sáng mai.
            String reason = (description != null && !description.isBlank())
                    ? "Urgent veterinary care pending: " + description.trim()
                    : "Urgent veterinary care pending";
            trainingDecisionService.block(horse, reason);
        }

        CareSchedule saved = careScheduleRepository.save(schedule);
        assignRequestedSchedule(saved);
        return CareScheduleResponse.from(saved);
    }

    private CareSchedule lockClinicalSchedule(Long scheduleId) {
        CareSchedule snapshot = careScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));
        // Match Manager review's admission -> horse -> schedule lock order.
        AdmissionApplication admission = snapshot.getAdmissionId() == null ? null
                : admissionRepository.findByIdForUpdate(snapshot.getAdmissionId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Admission not found"));
        Horse horse = horseRepository.findByIdForUpdate(snapshot.getHorseId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Horse not found"));
        CareSchedule locked = careScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));
        // The compatibility adapter may already have these entities in its persistence context.
        // Preserve this transaction's writes, then read the state committed before we acquired the locks.
        entityManager.flush();
        if (admission != null) entityManager.refresh(admission);
        entityManager.refresh(horse);
        entityManager.refresh(locked);
        return locked;
    }

    private void ensureAvailable(CareSchedule schedule, Long vetId, LocalDateTime start) {
        boolean vetHasConflict = careScheduleRepository.findByVeterinarianIdAndStatus(
                        vetId, CareScheduleStatus.IN_PROGRESS).stream()
                .anyMatch(other -> inProgressConflictsWithSlot(schedule, start, other));
        if (vetHasConflict
                || careScheduleRepository.existsByHorseIdAndStatus(schedule.getHorseId(), CareScheduleStatus.IN_PROGRESS)) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Veterinarian or horse has an examination in progress");
        }
        List<CareSchedule> booked = new ArrayList<>(careScheduleRepository.findScheduledForVet(vetId, CareScheduleStatus.SCHEDULED));
        if (schedule.getCareType() != CareType.URGENT) {
            booked.addAll(careScheduleRepository.findScheduledForHorse(schedule.getHorseId(), CareScheduleStatus.SCHEDULED));
        }
        for (CareSchedule other : booked) {
            if (Objects.equals(other.getId(), schedule.getId()) || !overlaps(schedule, start, other)) continue;
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Veterinarian or horse is already scheduled in this slot");
        }
    }

    private boolean overlaps(CareSchedule schedule, LocalDateTime start, CareSchedule other) {
        if (other.getScheduledAt() == null) return false;
        LocalDateTime end = start.plusMinutes(schedule.getDurationMinutes() > 0 ? schedule.getDurationMinutes() : 30);
        LocalDateTime otherEnd = other.getScheduledAt().plusMinutes(other.getDurationMinutes() > 0 ? other.getDurationMinutes() : 30);
        return start.isBefore(otherEnd) && end.isAfter(other.getScheduledAt());
    }

    @Transactional
    public CareScheduleResponse startCareSchedule(Long scheduleId, Long vetId) {
        userRepository.findById(vetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VET_NOT_FOUND", "Veterinarian not found"));
        CareSchedule schedule = lockClinicalSchedule(scheduleId);

        if (schedule.getStatus() != CareScheduleStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Only SCHEDULED care schedules can be started");
        }

        if (!Objects.equals(schedule.getVeterinarianId(), vetId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the assigned veterinarian can start this schedule");
        }

        schedule.setStatus(CareScheduleStatus.IN_PROGRESS);
        return CareScheduleResponse.from(careScheduleRepository.save(schedule));
    }

    @Transactional
    public CareScheduleResponse completeCareSchedule(Long scheduleId, CompleteCareScheduleRequest request, Long vetId) {
        validateCompletionRequest(request);

        CareSchedule schedule = lockClinicalSchedule(scheduleId);

        if (schedule.getStatus() == CareScheduleStatus.COMPLETED) {
            if (Objects.equals(schedule.getVeterinarianId(), vetId)) {
                if (request.getNextSchedule() != null) {
                    createNextSchedule(request.getNextSchedule(), vetId, schedule);
                }
                return CareScheduleResponse.from(schedule);
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the assigned veterinarian can access this completed schedule");
        }

        if (schedule.getStatus() != CareScheduleStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Care schedule must be IN_PROGRESS to complete");
        }
        if (!Objects.equals(schedule.getVeterinarianId(), vetId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the assigned veterinarian can complete this schedule");
        }

        AdmissionApplication linkedAdmission = schedule.getAdmissionId() == null ? null
                : admissionRepository.findByIdForUpdate(schedule.getAdmissionId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Admission not found"));
        if (schedule.getCareType() == CareType.INITIAL && (linkedAdmission == null
                || linkedAdmission.getStatus() != AdmissionStatus.VET_REVIEW)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REVIEW_STATE", "Admission is not awaiting a vet examination");
        }
        Horse horse = horseRepository.findByIdForUpdate(schedule.getHorseId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Horse not found"));

        TrainingDecision decision = request.getTrainingDecision();
        List<CareScheduleStatus> activeStatuses = List.of(
                CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS);
        boolean anotherActiveUrgent = schedule.getCareType() == CareType.URGENT
                ? careScheduleRepository.existsByHorseIdAndCareTypeAndStatusInAndIdNot(
                        horse.getId(), CareType.URGENT, activeStatuses, schedule.getId())
                : careScheduleRepository.existsByHorseIdAndCareTypeAndStatusIn(
                        horse.getId(), CareType.URGENT, activeStatuses);
        // Ngựa chỉ có MỘT trạng thái khóa nhưng có thể có nhiều ca khám cùng lúc.
        // Còn một ca khẩn cấp khác chưa xong thì lần khám này không được mở khóa:
        // kết quả vẫn lưu vào bệnh án bên dưới, nhưng ngựa giữ nguyên BLOCKED.
        boolean preservedUrgentLock = anotherActiveUrgent
                && horse.getTrainingDecision() == TrainingDecision.BLOCKED
                && decision == TrainingDecision.ALLOWED;
        if (!preservedUrgentLock) {
            if (decision == TrainingDecision.ALLOWED) {
                trainingDecisionService.allow(horse);
            } else {
                trainingDecisionService.block(horse, request.getRestrictionDetails().trim());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        saveExaminationResults(schedule, horse, request, vetId, now);

        // If exam linked to AdmissionApplication, update admission status
        if (schedule.getAdmissionId() != null) {
            AdmissionApplication admission = linkedAdmission;
            if (admission != null) {
                boolean isInitial = schedule.getCareType() == CareType.INITIAL;
                // Only INITIAL examinations own the Admission workflow. ROUTINE care must
                // not move an Admission backward even when it references a legacy admission.
                if (isInitial) {
                    admission.setVeterinarianId(vetId);
                    admission.setVetReviewedAt(now);
                    admission.setVetFeedback(request.getNotes() != null && !request.getNotes().isBlank()
                            ? request.getNotes() : request.getFindings());
                    // Thú y không duyệt/từ chối đơn: đơn luôn đi tiếp sang Trainer, kèm
                    // kết luận được tập hay tạm nghỉ. Ngựa vẫn là CANDIDATE nên chưa tập được
                    // cho tới khi Manager duyệt — không cần khóa thêm.
                    admission.setVetTrainingDecision(request.getTrainingDecision());
                    admission.setStatus(AdmissionStatus.TRAINER_REVIEW);
                    admissionRepository.save(admission);
                }
            }
        }

        schedule.setStatus(CareScheduleStatus.COMPLETED);
        schedule.setCompletedAt(now);
        CareSchedule completedSchedule = careScheduleRepository.save(schedule);

        // Gán Trainer chạy SAU khi transaction này commit (TrainerAssignmentTriggers):
        // lỗi lúc gán không thể làm rollback kết quả khám của Vet.
        if (schedule.getAdmissionId() != null && schedule.getCareType() == CareType.INITIAL) {
            eventPublisher.publishEvent(new InitialExamCompletedEvent(schedule.getAdmissionId()));
        }

        if (schedule.getCareType() == CareType.URGENT && schedule.getSourceIncidentId() != null) {
            incidentReportRepository.findById(schedule.getSourceIncidentId()).ifPresent(incident -> {
                if (incident.getStatus() == IncidentStatus.IN_REVIEW || incident.getStatus() == IncidentStatus.REPORTED) {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setHandledById(vetId);
                    incident.setHandledAt(now);
                    // The Groom who reported it reads the outcome on the incident, not on the health record.
                    incident.setHandlerNote(incidentOutcomeNote(request));
                    incidentReportRepository.save(incident);
                }
            });
        }

        // Atomic next schedule creation if provided
        if (request.getNextSchedule() != null) {
            createNextSchedule(request.getNextSchedule(), vetId, completedSchedule);
        }

        return CareScheduleResponse.from(completedSchedule);
    }

    private void validateCompletionRequest(CompleteCareScheduleRequest request) {
        if (request.getFindings() == null || request.getFindings().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Findings are required");
        }
        if (request.getDiagnosis() == null || request.getDiagnosis().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Diagnosis is required");
        }
        if (request.getTrainingDecision() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Training decision is required");
        }
        if (request.getTrainingDecision() == TrainingDecision.BLOCKED) {
            if (request.getRestrictionDetails() == null || request.getRestrictionDetails().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Restriction details are required when training is BLOCKED");
            }
            // Chặn tập luôn phải có điểm kết thúc: lần khám lại chính là mốc "tạm nghỉ đến".
            // Không có lịch thì ngựa bị khóa vô thời hạn cho tới khi ai đó nhớ ra mà tạo.
            if (request.getNextSchedule() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FOLLOW_UP_REQUIRED",
                        "A follow-up examination (nextSchedule) is required when training is BLOCKED");
            }
        }
    }

    private void saveExaminationResults(CareSchedule schedule, Horse horse,
                                        CompleteCareScheduleRequest request, Long vetId, LocalDateTime now) {
        HealthRecord record = new HealthRecord();
        record.setHorseId(horse.getId());
        record.setVeterinarianId(vetId);
        record.setCareScheduleId(schedule.getId());
        record.setRecordType(schedule.getCareType().name());
        record.setExaminedAt(now);
        record.setFindings(request.getFindings().trim());
        record.setDiagnosis(request.getDiagnosis().trim());
        record.setTreatment(request.getTreatment() != null ? request.getTreatment().trim() : null);
        record.setTrainingDecision(request.getTrainingDecision());
        record.setRestrictionDetails(request.getRestrictionDetails() != null ? request.getRestrictionDetails().trim() : null);
        record.setSymptoms(request.getSymptoms() != null ? request.getSymptoms().trim() : null);
        record.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);
        HealthRecord savedRecord = healthRecordRepository.save(record);

        // Save metrics if present
        if (request.getMetrics() != null) {
            for (HorseHealthMetricRequest metricReq : request.getMetrics()) {
                HorseHealthMetric m = new HorseHealthMetric();
                m.setHorseId(horse.getId());
                m.setHealthRecordId(savedRecord.getId());
                m.setRecordedAt(now);
                m.setHeartRate(metricReq.getHeartRate());
                m.setTemperature(metricReq.getTemperature());
                m.setWeight(metricReq.getWeight());
                m.setRespiratoryRate(metricReq.getRespiratoryRate());
                m.setHydrationStatus(metricReq.getHydrationStatus());
                m.setBodyConditionScore(metricReq.getBodyConditionScore());
                m.setNotes(metricReq.getNotes());
                metricRepository.save(m);
            }
        }
    }

    @Transactional
    public CareScheduleResponse cancelCareSchedule(Long scheduleId, CancelCareScheduleRequest request, Long userId) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Cancel reason is required");
        }

        CareSchedule schedule = careScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));

        if (schedule.getStatus() != CareScheduleStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Only SCHEDULED care schedules can be cancelled");
        }

        User actor = userRepository.findById(userId).orElse(null);
        boolean isManager = actor != null && actor.getRole() != null && "CLUB_MANAGER".equals(actor.getRole().getName());

        if (!isManager) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only club managers can cancel care schedules; veterinarians cannot decline assignments");
        }

        // Ngựa đang tạm nghỉ chỉ được mở khóa bởi một lần khám. Hủy nốt lần khám
        // cuối cùng thì ngựa bị khóa vô thời hạn mà không còn ai để mở.
        Horse horse = horseRepository.findById(schedule.getHorseId()).orElse(null);
        if (horse != null && horse.getTrainingDecision() == TrainingDecision.BLOCKED
                && !careScheduleRepository.existsByHorseIdAndStatusInAndIdNot(horse.getId(),
                        List.of(CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS),
                        schedule.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_FOLLOW_UP",
                    "This horse is blocked from training and this is its last pending examination. "
                            + "Schedule a replacement examination before cancelling this one.");
        }

        schedule.setStatus(CareScheduleStatus.CANCELLED);
        schedule.setCancelReason(request.getReason().trim());
        careScheduleRepository.save(schedule);

        return CareScheduleResponse.from(schedule);
    }

    @Transactional(readOnly = true)
    public CareScheduleDetailResponse getScheduleDetail(Long scheduleId) {
        CareSchedule schedule = careScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));
        Horse horse = horseRepository.findById(schedule.getHorseId()).orElse(null);
        User vet = schedule.getVeterinarianId() != null ? userRepository.findById(schedule.getVeterinarianId()).orElse(null) : null;
        HealthRecord hr = healthRecordRepository.findByCareScheduleId(scheduleId).orElse(null);

        return CareScheduleDetailResponse.of(schedule, horse, vet, hr);
    }

    @Transactional(readOnly = true)
    public CareScheduleDetailResponse getAssignedScheduleDetail(Long scheduleId, Long vetId) {
        CareSchedule schedule = careScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));
        if (!Objects.equals(schedule.getVeterinarianId(), vetId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the assigned veterinarian can view this schedule");
        }
        return getScheduleDetail(scheduleId);
    }

    @Transactional(readOnly = true)
    public CareScheduleDetailResponse getScheduleDetailForAdmissionAssignee(Long scheduleId, Long userId,
            String role) {
        CareSchedule schedule = careScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));
        AdmissionApplication admission = schedule.getAdmissionId() == null ? null
                : admissionRepository.findById(schedule.getAdmissionId()).orElse(null);
        boolean assigned = admission != null && "GROOM".equals(role) && Objects.equals(admission.getGroomId(), userId);
        if (!assigned) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This schedule is not assigned to you");
        }
        return getScheduleDetail(scheduleId);
    }

    @Transactional(readOnly = true)
    public Page<CareScheduleResponse> listSchedules(CareScheduleStatus status, CareType careType,
            Long horseId, Long vetId, Long admissionId, Pageable pageable) {
        return careScheduleRepository.findFiltered(status, careType, horseId, vetId, admissionId, pageable)
                .map(CareScheduleResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<CareScheduleResponse> listSchedulesForAdmissionAssignee(CareScheduleStatus status, CareType careType,
            Long horseId, Long vetId, Long admissionId, Long userId, String role, Pageable pageable) {
        if (!"GROOM".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only grooms can list care schedules by admission assignment");
        }
        return careScheduleRepository.findFilteredForGroom(status, careType, horseId, vetId,
                admissionId, userId, pageable).map(CareScheduleResponse::from);
    }

    @Transactional
    public CareScheduleResponse createNextSchedule(CreateNextScheduleRequest request, Long userId) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "nextSchedule is required");
        }
        if (request.getSourceScheduleId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "sourceScheduleId is required for standalone follow-up scheduling");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VET_NOT_FOUND", "Veterinarian not found"));
        CareSchedule source = careScheduleRepository.findByIdForUpdate(request.getSourceScheduleId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SOURCE_SCHEDULE_NOT_FOUND",
                        "Source examination not found"));
        if (source.getStatus() != CareScheduleStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_SOURCE_SCHEDULE",
                    "Source examination must be completed");
        }
        if (!Objects.equals(source.getVeterinarianId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only the veterinarian who completed the source examination may schedule its follow-up care");
        }
        return createNextSchedule(request, userId, source, true);
    }

    private CareScheduleResponse createNextSchedule(CreateNextScheduleRequest request, Long userId, CareSchedule source) {
        return createNextSchedule(request, userId, source, false);
    }

    private CareScheduleResponse createNextSchedule(CreateNextScheduleRequest request, Long userId,
            CareSchedule source, boolean standalone) {
        if (request == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "nextSchedule is required");
        Long horseId = source == null ? request.getHorseId() : source.getHorseId();
        Long admissionId = source == null ? request.getAdmissionId() : source.getAdmissionId();
        if (horseId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "horseId is required");
        if (source != null) {
            if (request.getSourceScheduleId() != null && !Objects.equals(request.getSourceScheduleId(), source.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "SOURCE_SCHEDULE_MISMATCH",
                        "nextSchedule sourceScheduleId must match the completed examination");
            }
            if (request.getHorseId() != null && !Objects.equals(request.getHorseId(), horseId)) {
                throw new ApiException(HttpStatus.CONFLICT, "HORSE_MISMATCH", "nextSchedule horseId must match the completed examination");
            }
            if (request.getAdmissionId() != null && !Objects.equals(request.getAdmissionId(), admissionId)) {
                throw new ApiException(HttpStatus.CONFLICT, "ADMISSION_MISMATCH", "nextSchedule admissionId must match the completed examination");
            }
        }
        if (request.getCareType() != CareType.ROUTINE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CARE_TYPE", "Follow-up schedules must use careType ROUTINE");
        }
        String description = request.getDescription() == null ? null : request.getDescription().trim();
        if (description == null || description.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Description is required for a follow-up schedule");
        }
        if (request.getScheduledAt() != null && request.getScheduledDate() != null && !request.getScheduledDate().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Provide either scheduledAt or scheduledDate, not both");
        }
        LocalDateTime requestedAt = request.getScheduledAt();
        if (requestedAt == null && request.getScheduledDate() != null && !request.getScheduledDate().isBlank()) {
            try {
                requestedAt = LocalDate.parse(request.getScheduledDate()).atTime(14, 0);
            } catch (java.time.format.DateTimeParseException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "scheduledDate must use ISO format yyyy-MM-dd");
            }
        }
        if (requestedAt == null || !requestedAt.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Requested schedule time must be in the future");
        }
        String operationKey = standalone
                ? request.getIdempotencyKey() == null ? null : request.getIdempotencyKey().trim()
                : "completion:" + source.getId();
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A valid idempotencyKey is required");
        }
        // Serialize standalone idempotency keys per actor, including requests for
        // different horses, before checking the unique (actor,key) contract.
        Horse horse = horseRepository.findByIdForUpdate(horseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Horse not found"));
        if (admissionId != null) {
            AdmissionApplication admission = admissionRepository.findById(admissionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Admission not found"));
            if (!Objects.equals(admission.getHorseId(), horseId)) {
                throw new ApiException(HttpStatus.CONFLICT, "ADMISSION_MISMATCH", "Admission does not belong to the scheduled horse");
            }
        }
        String fingerprint = fingerprint(horseId, admissionId, requestedAt, description);
        Optional<CareSchedule> existing = standalone
                ? careScheduleRepository.findByRequestedByIdAndIdempotencyKey(userId, operationKey)
                : careScheduleRepository.findBySourceScheduleId(source.getId());
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getRequestFingerprint(), fingerprint)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used with a different payload");
            }
            return CareScheduleResponse.from(existing.get());
        }

        CareSchedule schedule = new CareSchedule();
        schedule.setHorseId(horse.getId());
        schedule.setAdmissionId(admissionId);
        // sourceScheduleId on the entity is the one-to-one completion-operation key.
        // Standalone requests use (requestedById, idempotencyKey) so multiple distinct
        // follow-ups may be authorized by the same completed examination.
        schedule.setSourceScheduleId(standalone ? null : source.getId());
        schedule.setRequestedById(userId);
        schedule.setIdempotencyKey(operationKey);
        schedule.setRequestFingerprint(fingerprint);
        schedule.setCareType(CareType.ROUTINE);
        schedule.setStatus(CareScheduleStatus.REQUESTED);
        schedule.setDurationMinutes(DEFAULT_DURATION_MINUTES);
        schedule.setDescription(description);
        schedule.setRequestedAt(requestedAt);
        CareSchedule saved = careScheduleRepository.saveAndFlush(schedule);
        assignRequestedSchedule(saved);
        return CareScheduleResponse.from(saved);
    }

    private static String fingerprint(Long horseId, Long admissionId, LocalDateTime requestedAt, String description) {
        String value = horseId + "|" + admissionId + "|ROUTINE|" + requestedAt + "|" + description;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Transactional
    public void assignRequestedSchedules() {
        for (CareSchedule schedule : careScheduleRepository.lockNextRequestedBatch(ASSIGNMENT_BATCH_SIZE)) {
            assignRequestedSchedule(schedule);
        }
    }

    void assignRequestedSchedule(CareSchedule schedule) {
        if (schedule.getStatus() != CareScheduleStatus.REQUESTED) return;

        Horse horse = horseRepository.findByIdForUpdate(schedule.getHorseId()).orElse(null);
        if (horse == null) return;

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Integer> overdueAppointmentsByVet = new HashMap<>();
        Map<Long, Integer> scheduledMinutesByVet = new HashMap<>();
        for (CareSchedule assigned : careScheduleRepository
                .findByStatusInAndVeterinarianIdIsNotNull(List.of(
                        CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS))) {
            if (assigned.getVeterinarianId() == null) continue;
            scheduledMinutesByVet.merge(assigned.getVeterinarianId(),
                    assigned.getDurationMinutes() > 0 ? assigned.getDurationMinutes() : DEFAULT_DURATION_MINUTES,
                    Integer::sum);
            if (assigned.getScheduledAt() != null && assigned.getScheduledAt().isBefore(now)
                    && (assigned.getStatus() == CareScheduleStatus.SCHEDULED
                            || assigned.getStatus() == CareScheduleStatus.IN_PROGRESS)) {
                overdueAppointmentsByVet.merge(assigned.getVeterinarianId(), 1, Integer::sum);
            }
        }

        // Admission INITIAL exams must get a named Vet as soon as the horse arrives,
        // even when that Vet is currently busy or has no schedulable slot yet.
        // Keep the schedule REQUESTED until a non-conflicting appointment time is found.
        if (schedule.getCareType() == CareType.INITIAL && schedule.getVeterinarianId() == null) {
            User assignedVet = assignInitialExamToLeastLoadedVet(overdueAppointmentsByVet, scheduledMinutesByVet);
            if (assignedVet == null) return;
            schedule.setVeterinarianId(assignedVet.getId());
            careScheduleRepository.save(schedule);
            assignInitialAdmissionVet(schedule, assignedVet.getId());
        }

        LocalDateTime slot = findAssignmentSlot(schedule, overdueAppointmentsByVet, scheduledMinutesByVet);
        if (slot == null) return;

        List<User> ranked = rankVeterinarians(schedule, slot, overdueAppointmentsByVet, scheduledMinutesByVet);
        for (User candidate : ranked) {
            User lockedVet = userRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (!isStillEligible(lockedVet)) continue;
            try {
                ensureAvailable(schedule, lockedVet.getId(), slot);
            } catch (ApiException conflict) {
                continue;
            }

            schedule.setVeterinarianId(lockedVet.getId());
            schedule.setScheduledAt(slot);
            schedule.setStatus(CareScheduleStatus.SCHEDULED);

            careScheduleRepository.save(schedule);
            assignInitialAdmissionVet(schedule, lockedVet.getId());

            notificationService.sendAssignmentNotification(
                    lockedVet.getId(),
                    NotificationTypes.REFERENCE_CARE_SCHEDULE,
                    schedule.getId(),
                    horse.getId(),
                    NotificationTypes.ADMISSION_VET_ASSIGNED,
                    "New horse assignment",
                    "You have been assigned to candidate horse " + horse.getName() + " for care schedule #" + schedule.getId() + "."
            );

            if (schedule.getCareType() == CareType.URGENT) {
                GroomIncidentReport incident = incidentReportRepository.findByIdForUpdate(schedule.getSourceIncidentId())
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INCIDENT_NOT_FOUND", "Urgent incident not found"));
                LocalDateTime assignedAt = LocalDateTime.now();
                incident.setStatus(IncidentStatus.IN_REVIEW);
                incident.setHandledById(lockedVet.getId());
                incident.setHandledAt(assignedAt);
                incidentReportRepository.save(incident);
                eventPublisher.publishEvent(new UrgentAssignmentCommittedEvent(
                        buildUrgentAlert(schedule, incident, horse, lockedVet.getId(), assignedAt)));
            }

            recordAssignmentAudit(schedule, lockedVet);
            return;
        }
    }

    private void assignInitialAdmissionVet(CareSchedule schedule, Long veterinarianId) {
        if (schedule.getCareType() != CareType.INITIAL || schedule.getAdmissionId() == null) return;

        admissionRepository.findByIdForUpdate(schedule.getAdmissionId()).ifPresent(admission -> {
            if (!Objects.equals(admission.getVeterinarianId(), veterinarianId)) {
                admission.setVeterinarianId(veterinarianId);
                admissionRepository.save(admission);
            }
        });
    }

    private User assignInitialExamToLeastLoadedVet(Map<Long, Integer> overdueAppointmentsByVet,
            Map<Long, Integer> scheduledMinutesByVet) {
        List<User> ranked = Optional.ofNullable(userRepository.findActiveVeterinarians()).orElseGet(List::of)
                .stream()
                .sorted(Comparator
                        .comparingInt((User vet) -> overdueAppointmentsByVet.getOrDefault(vet.getId(), 0))
                        .thenComparingInt(vet -> scheduledMinutesByVet.getOrDefault(vet.getId(), 0))
                        .thenComparing(User::getId))
                .toList();

        for (User candidate : ranked) {
            User lockedVet = userRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (isStillEligible(lockedVet)) return lockedVet;
        }
        return null;
    }

    private LocalDateTime findAssignmentSlot(CareSchedule schedule,
            Map<Long, Integer> overdueAppointmentsByVet, Map<Long, Integer> scheduledMinutesByVet) {
        if (schedule.getCareType() == CareType.URGENT) return LocalDateTime.now();

        LocalDateTime requested = schedule.getRequestedAt();
        LocalDate startDate = (requested != null && requested.isAfter(LocalDateTime.now())
                ? requested : LocalDateTime.now()).toLocalDate();
        for (int dayOffset = 0; dayOffset < 14; dayOffset++) {
            LocalDate day = startDate.plusDays(dayOffset);
            List<LocalTime> times = new ArrayList<>(List.of(
                    LocalTime.of(13, 30), LocalTime.of(14, 0),
                    LocalTime.of(14, 30), LocalTime.of(15, 0)));
            for (int hour = 8; hour <= 17; hour++) {
                for (int minute : List.of(0, 30)) {
                    LocalTime time = LocalTime.of(hour, minute);
                    if (!times.contains(time)) times.add(time);
                }
            }
            for (LocalTime time : times) {
                LocalDateTime slot = day.atTime(time);
                if (requested != null && slot.isBefore(requested)) continue;
                if (slot.isBefore(LocalDateTime.now())) continue;
                if (!horseHasConflict(schedule, slot)
                        && !rankVeterinarians(schedule, slot, overdueAppointmentsByVet, scheduledMinutesByVet).isEmpty()) {
                    return slot;
                }
            }
        }
        return null;
    }

    private boolean horseHasConflict(CareSchedule schedule, LocalDateTime slot) {
        if (careScheduleRepository.existsByHorseIdAndStatus(schedule.getHorseId(), CareScheduleStatus.IN_PROGRESS)) {
            return true;
        }
        return careScheduleRepository.findScheduledForHorse(schedule.getHorseId(), CareScheduleStatus.SCHEDULED)
                .stream().anyMatch(other -> !Objects.equals(other.getId(), schedule.getId()) && overlaps(schedule, slot, other));
    }

    private List<User> rankVeterinarians(CareSchedule schedule, LocalDateTime slot,
            Map<Long, Integer> overdueAppointmentsByVet, Map<Long, Integer> scheduledMinutesByVet) {
        List<User> vets = schedule.getCareType() == CareType.INITIAL && schedule.getVeterinarianId() != null
                ? userRepository.findById(schedule.getVeterinarianId()).stream().toList()
                : Optional.ofNullable(userRepository.findActiveVeterinarians()).orElseGet(List::of);
        LocalDateTime dayStart = slot.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<CareSchedule> assigned = careScheduleRepository.findAssignedInDay(dayStart, dayEnd,
                List.of(CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS));

        Map<Long, Set<Long>> horsesByVet = new HashMap<>();
        Map<Long, Integer> minutesByVet = new HashMap<>();
        for (CareSchedule item : assigned) {
            horsesByVet.computeIfAbsent(item.getVeterinarianId(), ignored -> new HashSet<>()).add(item.getHorseId());
            minutesByVet.merge(item.getVeterinarianId(),
                    item.getDurationMinutes() > 0 ? item.getDurationMinutes() : DEFAULT_DURATION_MINUTES,
                    Integer::sum);
        }

        return vets.stream()
                .filter(v -> schedule.getCareType() == CareType.INITIAL && schedule.getVeterinarianId() != null
                        ? Objects.equals(v.getId(), schedule.getVeterinarianId())
                        : !careScheduleRepository.existsByVeterinarianIdAndStatus(v.getId(), CareScheduleStatus.IN_PROGRESS))
                .filter(v -> careScheduleRepository.findByVeterinarianIdAndStatus(
                                v.getId(), CareScheduleStatus.IN_PROGRESS).stream()
                        .noneMatch(other -> inProgressConflictsWithSlot(schedule, slot, other)))
                .filter(v -> careScheduleRepository.findScheduledForVet(v.getId(), CareScheduleStatus.SCHEDULED)
                        .stream().noneMatch(other -> overlaps(schedule, slot, other)))
                .sorted(Comparator
                        .comparingInt((User v) -> overdueAppointmentsByVet.getOrDefault(v.getId(), 0))
                        .thenComparingInt(v -> scheduledMinutesByVet.getOrDefault(v.getId(), 0))
                        .thenComparingInt(v -> horsesByVet.getOrDefault(v.getId(), Set.of()).size())
                        .thenComparingInt(v -> minutesByVet.getOrDefault(v.getId(), 0))
                        .thenComparing((User v) -> schedule.getCareType() == CareType.URGENT ? false
                                : !careScheduleRepository.existsByVeterinarianIdAndHorseIdAndStatusIn(
                                        v.getId(), schedule.getHorseId(),
                                        List.of(CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS, CareScheduleStatus.COMPLETED)))
                        .thenComparing(User::getId))
                .toList();
    }

    private boolean inProgressConflictsWithSlot(CareSchedule schedule, LocalDateTime slot, CareSchedule inProgress) {
        if (schedule.getCareType() != CareType.INITIAL) return true;
        LocalDateTime scheduledAt = inProgress.getScheduledAt();
        if (scheduledAt == null) return true;
        int duration = inProgress.getDurationMinutes() > 0
                ? inProgress.getDurationMinutes() : DEFAULT_DURATION_MINUTES;
        // An overdue in-progress visit remains active; retain the Vet assignment but
        // do not book a conflicting appointment until that visit is completed.
        if (!scheduledAt.plusMinutes(duration).isAfter(LocalDateTime.now())) return true;
        return overlaps(schedule, slot, inProgress);
    }

    private boolean isStillEligible(User vet) {
        return vet != null && vet.isActive() && vet.getRole() != null
                && "VETERINARIAN".equals(vet.getRole().getName())
                && veterinarianProfileRepository.findById(vet.getId())
                        .map(profile -> profile.getLicenseNumber() != null && !profile.getLicenseNumber().isBlank())
                        .orElse(false);
    }

    private void recordAssignmentAudit(CareSchedule schedule, User veterinarian) {
        AuditLog audit = new AuditLog();
        audit.setAction("CARE_SCHEDULE_AUTO_ASSIGNED");
        audit.setEntityName("CareSchedule");
        audit.setEntityId(schedule.getId());
        audit.setUser(veterinarian);
        auditLogRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<UrgentAssignmentAlert> getPendingUrgentAlerts(Long veterinarianId) {
        return careScheduleRepository.findByVeterinarianIdAndCareTypeAndStatusIn(
                        veterinarianId, CareType.URGENT,
                        List.of(CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS)).stream()
                .map(schedule -> {
                    GroomIncidentReport incident = schedule.getSourceIncidentId() != null
                            ? incidentReportRepository.findById(schedule.getSourceIncidentId()).orElse(null)
                            : null;
                    Horse horse = horseRepository.findById(schedule.getHorseId()).orElse(null);
                    return horse == null ? null
                            : buildUrgentAlert(schedule, incident, horse, veterinarianId, schedule.getUpdatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public UrgentAssignmentAlert getUrgentCase(Long scheduleId, Long veterinarianId) {
        CareSchedule schedule = careScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Care schedule not found"));
        if (schedule.getCareType() != CareType.URGENT) {
            throw new ApiException(HttpStatus.NOT_FOUND, "URGENT_CASE_NOT_FOUND", "Urgent case not found");
        }
        if (!Objects.equals(schedule.getVeterinarianId(), veterinarianId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the assigned veterinarian can view this urgent case");
        }
        GroomIncidentReport incident = schedule.getSourceIncidentId() != null
                ? incidentReportRepository.findById(schedule.getSourceIncidentId()).orElse(null)
                : null;
        Horse horse = horseRepository.findById(schedule.getHorseId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Horse not found"));
        return buildUrgentAlert(schedule, incident, horse, veterinarianId, schedule.getUpdatedAt());
    }

    /** What the reporting Groom is told when the urgent case closes: diagnosis, care to give, and whether the horse may train. */
    private static String incidentOutcomeNote(CompleteCareScheduleRequest request) {
        StringBuilder note = new StringBuilder("Diagnosis: ").append(request.getDiagnosis().trim());
        if (request.getTreatment() != null && !request.getTreatment().isBlank()) {
            note.append("\nTreatment: ").append(request.getTreatment().trim());
        }
        if (request.getTrainingDecision() == TrainingDecision.BLOCKED) {
            note.append("\nTraining: blocked");
            if (request.getRestrictionDetails() != null && !request.getRestrictionDetails().isBlank()) {
                note.append(" - ").append(request.getRestrictionDetails().trim());
            }
        } else {
            note.append("\nTraining: allowed");
        }
        // handler_note is a TEXT column, but keep the note a readable size.
        return note.length() > 2000 ? note.substring(0, 2000) : note.toString();
    }

    private UrgentAssignmentAlert buildUrgentAlert(CareSchedule schedule, GroomIncidentReport incident,
            Horse horse, Long veterinarianId, LocalDateTime assignedAt) {
        User reporter = incident != null && incident.getGroomId() != null
                ? userRepository.findById(incident.getGroomId()).orElse(null) : null;
        StableStall stall = horse.getCurrentStallId() == null ? null
                : stallRepository.findById(horse.getCurrentStallId()).orElse(null);
        long eventId = schedule.getId();
        return new UrgentAssignmentAlert(
                eventId,
                schedule.getId(),
                incident != null ? incident.getId() : null,
                veterinarianId,
                horse.getId(),
                horse.getName(),
                horse.getStableLocation(),
                stall != null ? stall.getStallCode() : null,
                incident != null ? incident.getGroomId() : null,
                reporter != null ? reporter.getFullName() : null,
                incident != null ? incident.getReportedAt() : schedule.getCreatedAt(),
                incident != null ? incident.getSeverity() : null,
                incident != null ? incident.getTitle() : "Urgent Care",
                incident != null ? incident.getDescription() : schedule.getDescription(),
                // The image endpoint, never the storage key: the client loads it with its access token.
                incident != null && incident.getImageUrl() != null
                        ? "/api/groom-incident-reports/" + incident.getId() + "/image" : null,
                horse.getTrainingDecision(),
                schedule.getStatus(),
                schedule.getScheduledAt(),
                assignedAt
        );
    }
}
