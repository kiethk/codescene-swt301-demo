package com.rtms.backend.admission.service;
import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.admission.enums.AdmissionDocumentType;
import com.rtms.backend.medical.entity.CareSchedule;


import com.rtms.backend.config.ApiException;
import com.rtms.backend.admission.dto.AdmissionCapacitySummary;
import com.rtms.backend.admission.dto.VetAdmissionQueueItemResponse;
import com.rtms.backend.admission.dto.VetQueueSummaryResponse;
import com.rtms.backend.medical.dto.CareScheduleResponse;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
import com.rtms.backend.admission.dto.AdmissionDetailResponse;
import com.rtms.backend.admission.dto.AdmissionDocumentResponse;
import com.rtms.backend.admission.dto.AdmissionSummaryResponse;
import com.rtms.backend.admission.dto.GroomAdmissionQueueResponse;
import com.rtms.backend.medical.dto.InitialExamScheduleResponse;
import com.rtms.backend.admission.dto.TrainerAdmissionQueueResponse;
import com.rtms.backend.admission.entity.AdmissionApplication;
import com.rtms.backend.admission.entity.AdmissionDocument;
import com.rtms.backend.admission.entity.CandidateHorseProfile;
import com.rtms.backend.medical.entity.HealthRecord;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.admission.repository.AdmissionDocumentRepository;
import com.rtms.backend.admission.repository.CandidateHorseProfileRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.medical.repository.CareScheduleRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdmissionQueryService {

    private final AdmissionApplicationRepository admissionApplicationRepository;
    private final CandidateHorseProfileRepository candidateHorseProfileRepository;
    private final AdmissionDocumentRepository admissionDocumentRepository;
    private final StableStallRepository stableStallRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final FileStorageService fileStorage;
    private final UserRepository userRepository;
    private final CareScheduleRepository careScheduleRepository;

    @Autowired
    public AdmissionQueryService(
            AdmissionApplicationRepository admissionApplicationRepository,
            CandidateHorseProfileRepository candidateHorseProfileRepository,
            AdmissionDocumentRepository admissionDocumentRepository,
            StableStallRepository stableStallRepository,
            HealthRecordRepository healthRecordRepository,
            FileStorageService fileStorage,
            UserRepository userRepository,
            CareScheduleRepository careScheduleRepository) {
        this.admissionApplicationRepository = admissionApplicationRepository;
        this.candidateHorseProfileRepository = candidateHorseProfileRepository;
        this.admissionDocumentRepository = admissionDocumentRepository;
        this.stableStallRepository = stableStallRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.fileStorage = fileStorage;
        this.userRepository = userRepository;
        this.careScheduleRepository = careScheduleRepository;
    }

    public AdmissionQueryService(
            AdmissionApplicationRepository admissionApplicationRepository,
            CandidateHorseProfileRepository candidateHorseProfileRepository,
            AdmissionDocumentRepository admissionDocumentRepository,
            StableStallRepository stableStallRepository,
            HealthRecordRepository healthRecordRepository,
            FileStorageService fileStorage,
            UserRepository userRepository) {
        this(admissionApplicationRepository, candidateHorseProfileRepository, admissionDocumentRepository,
                stableStallRepository, healthRecordRepository, fileStorage, userRepository, null);
    }

    public List<AdmissionSummaryResponse> getAdmissions(AdmissionStatus status) {
        List<AdmissionApplication> admissions;

        if (status != null) {
            admissions = admissionApplicationRepository.findByStatus(status);
        } else {
            admissions = admissionApplicationRepository.findAll();
        }

        return admissions.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    /**
     * Hàng chờ riêng của một Huấn luyện viên.
     *
     * KHÔNG dùng getAdmissions(status) cho màn hình Trainer: endpoint đó
     * (GET /api/admissions) dùng chung cho cả 5 vai trò — Chủ ngựa, Groom,
     * Thú y, Trainer, Quản lý — vì cùng một quyền ADMISSION_APPLICATION_VIEW.
     * Nhét điều kiện trainer_id vào đó sẽ làm danh sách của Quản lý và Thú y
     * rỗng trắng, mà không gây lỗi biên dịch nào để báo trước.
     */
    public TrainerAdmissionQueueResponse getTrainerQueue(Long trainerId) {
        List<AdmissionSummaryResponse> pending = admissionApplicationRepository
                .findByTrainerIdAndStatusOrderBySubmittedAtAscIdAsc(trainerId, AdmissionStatus.TRAINER_REVIEW)
                .stream()
                .map(this::toSummaryResponse)
                .toList();

        List<AdmissionSummaryResponse> reviewed = admissionApplicationRepository
                .findByTrainerIdAndTrainerReviewedAtIsNotNullOrderByTrainerReviewedAtDesc(trainerId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();

        return new TrainerAdmissionQueueResponse(pending, reviewed);
    }

    public GroomAdmissionQueueResponse getGroomQueue(String candidateName, AdmissionStatus status,
            java.time.LocalDate submittedFrom, java.time.LocalDate submittedTo, int page, int size) {
        if (page < 0)
            throw new IllegalArgumentException("Page must be zero or greater");
        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo)) {
            throw new IllegalArgumentException("Submitted-from date must not be after submitted-to date");
        }
        int pageSize = size <= 0 ? 10 : Math.min(size, 10);
        String nameFilter = candidateName == null || candidateName.isBlank()
                ? ""
                : candidateName.trim();
        var pageable = PageRequest.of(page, pageSize,
                Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id")));
        Page<AdmissionApplication> result = admissionApplicationRepository.findGroomQueue(
                status != null,
                status == null ? AdmissionStatus.GROOM_REVIEW : status,
                !nameFilter.isEmpty(),
                nameFilter,
                submittedFrom != null,
                submittedFrom == null ? java.time.LocalDate.of(1, 1, 1).atStartOfDay()
                        : submittedFrom.atStartOfDay(),
                submittedTo != null,
                submittedTo == null
                        ? java.time.LocalDate.of(9999, 12, 31).atTime(23, 59, 59, 999_999_000)
                        : submittedTo.plusDays(1).atStartOfDay(),
                pageable);
        return new GroomAdmissionQueueResponse(
                result.getContent().stream().map(this::toSummaryResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    public AdmissionDetailResponse getAdmissionDetail(Long admissionId) {
        AdmissionApplication admission = admissionApplicationRepository
                .findById(admissionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Admission not found"));

        CandidateHorseProfile candidate = candidateHorseProfileRepository
                .findByAdmissionId(admissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Candidate horse profile not found"));

        List<AdmissionDocument> documents = admissionDocumentRepository.findByAdmissionId(admissionId);

        AdmissionDetailResponse response = new AdmissionDetailResponse();

        response.setAdmissionId(admission.getId());
        response.setOwnerId(admission.getOwnerId());
        response.setOwnerName(admission.getOwnerId() == null ? null
                : userRepository.findById(admission.getOwnerId())
                        .map(com.rtms.backend.identity.entity.User::getFullName).orElse(null));
        response.setStatus(admission.getStatus());
        response.setQuarantineStallId(admission.getQuarantineStallId());

        response.setCandidate(candidate);
        response.setDocuments(documents.stream().map(this::toDocumentResponse).toList());

        response.setGroomId(admission.getGroomId());
        response.setGroomDecision(admission.getGroomDecision());
        response.setGroomFeedback(admission.getGroomFeedback());
        response.setGroomReviewedAt(admission.getGroomReviewedAt());
        response.setArrivalDeadlineAt(admission.getArrivalDeadlineAt());
        response.setArrivedAt(admission.getArrivedAt());

        response.setVeterinarianId(admission.getVeterinarianId());
        response.setVetTrainingDecision(admission.getVetTrainingDecision());
        response.setVetFeedback(admission.getVetFeedback());
        response.setVetReviewedAt(admission.getVetReviewedAt());

        Long trainerId = admission.getTrainerId();
        response.setTrainerId(trainerId);
        response.setTrainerName(trainerId == null ? null
                : userRepository.findById(trainerId)
                        .map(com.rtms.backend.identity.entity.User::getFullName).orElse(null));

        response.setTrainerFeedback(admission.getTrainerFeedback());
        response.setTrainerReviewedAt(admission.getTrainerReviewedAt());

        response.setManagerId(admission.getManagerId());
        response.setManagerDecision(admission.getManagerDecision());
        response.setManagerFeedback(admission.getManagerFeedback());
        response.setManagerReviewedAt(admission.getManagerReviewedAt());

        response.setHorseId(admission.getHorseId());
        response.setSubmittedAt(admission.getSubmittedAt());

        response.setQuarantineStallCode(admission.getQuarantineStallId() == null
                ? null
                : stableStallRepository.findById(admission.getQuarantineStallId())
                        .map(StableStall::getStallCode)
                        .orElse(null));

        // Available REGULAR stalls — always included so Manager can select during approval
        response.setAvailableRegularStalls(
                stableStallRepository.findAllAvailableRegularStallsOrdered());
        long availableQuarantine = stableStallRepository.countAvailableQuarantineStalls();
        long availableRegular = stableStallRepository.countAvailableRegularStalls();
        long occupiedQuarantine = stableStallRepository.countOccupiedQuarantineStalls();
        response.setCapacity(new AdmissionCapacitySummary(
                availableQuarantine, availableRegular, occupiedQuarantine,
                AdmissionCapacityPolicy.isAvailable(availableQuarantine, availableRegular,
                        occupiedQuarantine),
                AdmissionCapacityPolicy.blockingReason(availableQuarantine, availableRegular,
                        occupiedQuarantine)));

        // Health records for the horse created during Vet quarantine review
        if (admission.getHorseId() != null) {
            if (careScheduleRepository != null) {
                careScheduleRepository.findFirstByAdmissionIdAndCareTypeOrderByCreatedAtDesc(admission.getId(), com.rtms.backend.medical.enums.CareType.INITIAL)
                        .ifPresent(schedule -> {
                            response.setInitialExamSchedule(InitialExamScheduleResponse.from(schedule));
                            if (response.getVeterinarianId() == null && schedule.getVeterinarianId() != null) {
                                response.setVeterinarianId(schedule.getVeterinarianId());
                            }
                        });
            }
            List<HealthRecord> healthRecords = healthRecordRepository.findByHorseIdOrderByExaminedAtDesc(
                    admission.getHorseId());
            response.setHealthRecords(healthRecords);
        }

        return response;
    }

    private AdmissionSummaryResponse toSummaryResponse(
            AdmissionApplication admission) {

        CandidateHorseProfile candidate = candidateHorseProfileRepository
                .findByAdmissionId(admission.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Candidate horse profile not found"));

        // Quarantine stall code is shown on the Trainer queue.
        // Applications without an allocated stall return null.
        String stallCode = admission.getQuarantineStallId() == null
                ? null
                : stableStallRepository.findById(admission.getQuarantineStallId())
                        .map(StableStall::getStallCode)
                        .orElse(null);

        String imageUrl = admissionDocumentRepository.findByAdmissionId(admission.getId()).stream()
                .filter(d -> com.rtms.backend.admission.enums.AdmissionDocumentType.HORSE_PHOTO
                        .equals(d.getDocumentType()))
                .findFirst()
                .map(this::documentUrl)
                .orElse(null);

        return new AdmissionSummaryResponse(
                admission.getId(),
                admission.getStatus(),
                candidate.getName(),
                candidate.getBreed(),
                candidate.getDateOfBirth(),
                admission.getSubmittedAt(),
                admission.getQuarantineStallId(),
                stallCode,
                imageUrl,
                admission.getTrainerId(),
                admission.getTrainerReviewedAt());
    }

    /** Where a client loads this document from: its cloud URL, or this API when it is on local disk. */
    private String documentUrl(AdmissionDocument document) {
        return fileStorage.publicUrl(document.getFileUrl(),
                "/api/admissions/" + document.getAdmissionId() + "/documents/" + document.getId() + "/file");
    }

    private AdmissionDocumentResponse toDocumentResponse(AdmissionDocument document) {
        return new AdmissionDocumentResponse(document.getId(), document.getDocumentType(),
                documentUrl(document), document.getOriginalFileName(),
                document.getRecordDate(),
                document.getNote(), document.getUploadedAt(), isMedical(document));
    }

    private boolean isMedical(AdmissionDocument document) {
        return switch (document.getDocumentType()) {
            case VACCINATION_RECORD, DEWORMING_RECORD, HEALTH_CERTIFICATE,
                    PREVIOUS_MEDICAL_RECORD, PREVIOUS_INJURY_RECORD ->
                true;
            default -> false;
        };
    }

    public void assertVetAssignedOrManager(Long admissionId, AuthenticatedUser user) {
        if ("CLUB_MANAGER".equals(user.getRole())) {
            return;
        }
        if ("VETERINARIAN".equals(user.getRole())) {
            boolean isAssigned = (careScheduleRepository != null && careScheduleRepository.existsByAdmissionIdAndVeterinarianId(admissionId, user.getUserId()))
                    || admissionApplicationRepository.findById(admissionId)
                            .map(a -> Objects.equals(a.getVeterinarianId(), user.getUserId()))
                            .orElse(false);
            if (!isAssigned) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not assigned to examine this admission");
            }
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    public List<AdmissionSummaryResponse> getAdmissionsForVet(Long vetId, AdmissionStatus status) {
        if (careScheduleRepository == null) return List.of();
        Set<Long> assignedIds = careScheduleRepository.findVetAdmissionSchedules(vetId).stream()
                .map(com.rtms.backend.medical.entity.CareSchedule::getAdmissionId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return admissionApplicationRepository.findAllById(assignedIds).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .sorted(Comparator.comparing(AdmissionApplication::getSubmittedAt).reversed()
                        .thenComparing(AdmissionApplication::getId).reversed())
                .map(this::toSummaryResponse)
                .toList();
    }

    public List<AdmissionSummaryResponse> getAdmissionsForTrainer(Long trainerId, AdmissionStatus status) {
        return admissionApplicationRepository.findByTrainerId(trainerId).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .sorted(Comparator.comparing(AdmissionApplication::getSubmittedAt).reversed()
                        .thenComparing(AdmissionApplication::getId).reversed())
                .map(this::toSummaryResponse)
                .toList();
    }

    public List<AdmissionSummaryResponse> getAdmissionsForGroom(Long groomId, AdmissionStatus status) {
        return admissionApplicationRepository.findByGroomId(groomId).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .sorted(Comparator.comparing(AdmissionApplication::getSubmittedAt).reversed()
                        .thenComparing(AdmissionApplication::getId).reversed())
                .map(this::toSummaryResponse)
                .toList();
    }

    /** Removes capacity/final-manager data that is unrelated to a Vet's assigned clinical work. */
    public AdmissionDetailResponse getVetAdmissionDetail(Long admissionId) {
        AdmissionDetailResponse response = getAdmissionDetail(admissionId);
        response.setCapacity(null);
        response.setAvailableRegularStalls(null);
        response.setManagerId(null);
        response.setManagerDecision(null);
        response.setManagerFeedback(null);
        response.setManagerReviewedAt(null);
        return response;
    }

    public Page<VetAdmissionQueueItemResponse> getVetQueue(
            Long vetId,
            String search,
            String pill,
            AdmissionStatus admissionStatus,
            CareScheduleStatus scheduleStatus,
            CareType careType,
            String priority,
            Pageable pageable) {

        if (careScheduleRepository == null) {
            return Page.empty(pageable);
        }

        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedPill = pill == null ? "" : pill.trim().toUpperCase(Locale.ROOT);
        String normalizedPriority = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        Page<com.rtms.backend.medical.entity.CareSchedule> schedulePage = careScheduleRepository.findVetQueuePage(
                vetId, !normalizedSearch.isBlank(), normalizedSearch, normalizedPill,
                admissionStatus, scheduleStatus, careType, normalizedPriority, pageable);
        if (schedulePage.isEmpty()) return Page.empty(pageable);

        Set<Long> admissionIds = schedulePage.getContent().stream()
                .map(com.rtms.backend.medical.entity.CareSchedule::getAdmissionId).collect(Collectors.toSet());
        Map<Long, AdmissionApplication> admissions = admissionApplicationRepository.findAllById(admissionIds).stream()
                .collect(Collectors.toMap(AdmissionApplication::getId, a -> a));
        Map<Long, CandidateHorseProfile> candidates = candidateHorseProfileRepository.findByAdmissionIdIn(admissionIds).stream()
                .collect(Collectors.toMap(CandidateHorseProfile::getAdmissionId, c -> c));
        Map<Long, Long> admissionTrainerMap = new HashMap<>();
        admissions.values().stream()
                .filter(a -> a.getTrainerId() != null)
                .forEach(a -> admissionTrainerMap.put(a.getId(), a.getTrainerId()));
        Set<Long> userIds = admissions.values().stream()
                .map(AdmissionApplication::getOwnerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        userIds.addAll(admissionTrainerMap.values());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Set<Long> stallIds = admissions.values().stream().map(AdmissionApplication::getQuarantineStallId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, StableStall> stalls = stableStallRepository.findAllById(stallIds).stream()
                .collect(Collectors.toMap(StableStall::getId, s -> s));

        List<VetAdmissionQueueItemResponse> items = schedulePage.getContent().stream().map(cs -> {
            AdmissionApplication adm = admissions.get(cs.getAdmissionId());
            CandidateHorseProfile cand = candidates.get(cs.getAdmissionId());
            if (adm == null || cand == null) return null;
            User owner = users.get(adm.getOwnerId());
            Long trainerId = admissionTrainerMap.get(adm.getId());
            User trainer = trainerId == null ? null : users.get(trainerId);
            StableStall stall = stalls.get(adm.getQuarantineStallId());
            return new VetAdmissionQueueItemResponse(
                    adm.getId(),
                    adm.getOwnerId(),
                    owner == null ? null : owner.getFullName(),
                    cand.getName(),
                    cand.getBreed(),
                    cand.getDateOfBirth(),
                    adm.getStatus(),
                    adm.getSubmittedAt(),
                    adm.getQuarantineStallId(),
                    stall == null ? null : stall.getStallCode(),
                    adm.getHorseId(),
                    trainerId,
                    trainer == null ? null : trainer.getFullName(),
                    CareScheduleResponse.from(cs)
            );
        }).filter(Objects::nonNull).toList();
        return new org.springframework.data.domain.PageImpl<>(items, pageable, schedulePage.getTotalElements());
    }

    public VetQueueSummaryResponse getVetQueueSummary(Long vetId) {
        if (careScheduleRepository == null) {
            return new VetQueueSummaryResponse(0, 0, 0);
        }
        long total = careScheduleRepository.countByVeterinarianIdAndAdmissionIdIsNotNullAndStatusIn(
                vetId, List.of(CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED,
                        CareScheduleStatus.IN_PROGRESS));
        long awaiting = careScheduleRepository.countByVeterinarianIdAndAdmissionIdIsNotNullAndStatusIn(
                vetId, List.of(CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED));
        long inProgress = careScheduleRepository.countByVeterinarianIdAndAdmissionIdIsNotNullAndStatusIn(
                vetId, List.of(CareScheduleStatus.IN_PROGRESS));
        return new VetQueueSummaryResponse(total, awaiting, inProgress);
    }
}
