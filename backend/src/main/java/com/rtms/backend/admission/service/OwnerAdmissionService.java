package com.rtms.backend.admission.service;
import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.admission.dto.AdmissionDocumentMetadataRequest;
import com.rtms.backend.admission.dto.AdmissionDocumentResponse;
import com.rtms.backend.admission.dto.AdmissionSummaryResponse;
import com.rtms.backend.admission.dto.CreateOwnerAdmissionRequest;
import com.rtms.backend.admission.dto.OwnerAdmissionDetailResponse;
import com.rtms.backend.admission.entity.AdmissionApplication;
import com.rtms.backend.admission.entity.AdmissionDocument;
import com.rtms.backend.admission.entity.CandidateHorseProfile;
import com.rtms.backend.admission.enums.AdmissionDocumentType;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.admission.repository.AdmissionDocumentRepository;
import com.rtms.backend.admission.repository.CandidateHorseProfileRepository;
import com.rtms.backend.horse.entity.Horse;


import com.rtms.backend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;


@Service
public class OwnerAdmissionService {
    private static final Set<AdmissionDocumentType> MEDICAL_TYPES = EnumSet.of(
            AdmissionDocumentType.VACCINATION_RECORD, AdmissionDocumentType.DEWORMING_RECORD,
            AdmissionDocumentType.HEALTH_CERTIFICATE, AdmissionDocumentType.PREVIOUS_MEDICAL_RECORD,
            AdmissionDocumentType.PREVIOUS_INJURY_RECORD);

    private static final Set<AdmissionDocumentType> REQUIRED_DOCUMENT_TYPES = EnumSet.of(
            AdmissionDocumentType.HORSE_PHOTO,
            AdmissionDocumentType.REGISTRATION_DOCUMENT,
            AdmissionDocumentType.PEDIGREE_CERTIFICATE,
            AdmissionDocumentType.VACCINATION_RECORD
    );

    private static final Logger log =  LoggerFactory.getLogger(OwnerAdmissionService.class);

    private final AdmissionApplicationRepository admissions;
    private final CandidateHorseProfileRepository candidates;
    private final AdmissionDocumentRepository documents;
    private final FileStorageService fileStorage;

    public OwnerAdmissionService(AdmissionApplicationRepository admissions,
            CandidateHorseProfileRepository candidates, AdmissionDocumentRepository documents,
            FileStorageService fileStorage) {
        this.admissions = admissions;
        this.candidates = candidates;
        this.documents = documents;
        this.fileStorage = fileStorage;
    }

    /** The transaction creates exactly one new Application and one new immutable snapshot. */
    @Transactional
    public OwnerAdmissionDetailResponse create(Long ownerId, CreateOwnerAdmissionRequest request) {
        AdmissionApplication application = new AdmissionApplication();
        application.setOwnerId(ownerId);
        application.setStatus(AdmissionStatus.GROOM_REVIEW);
        application = admissions.save(application);

        CandidateHorseProfile snapshot = new CandidateHorseProfile();
        snapshot.setAdmissionId(application.getId());
        snapshot.setName(request.name().trim());
        snapshot.setBreed(trimNullable(request.breed()));
        snapshot.setDateOfBirth(request.dateOfBirth());
        snapshot.setRegistrationNumber(ueln(request.registrationNumber()));
        snapshot.setRegistryName(trimNullable(request.registryName()));
        snapshot.setSireName(trimNullable(request.sireName()));
        snapshot.setSireRegistrationNumber(ueln(request.sireRegistrationNumber()));
        snapshot.setDamName(trimNullable(request.damName()));
        snapshot.setDamRegistrationNumber(ueln(request.damRegistrationNumber()));
        snapshot.setPedigreeNotes(trimNullable(request.pedigreeNotes()));
        candidates.save(snapshot);

        return toDetail(application, snapshot, List.of());
    }

    @Transactional
    public  OwnerAdmissionDetailResponse submit(
            Long ownerId,
            CreateOwnerAdmissionRequest request,
            List<AdmissionDocumentMetadataRequest> metadata,
            List<MultipartFile> files) {
        validateDocumentMetadata(metadata, files);
        validateFiles(metadata, files);

        List<String> storedKeys = registerFileCleanup();

        AdmissionApplication application = new AdmissionApplication();
        application.setOwnerId(ownerId);
        application.setStatus(AdmissionStatus.GROOM_REVIEW);
        application = admissions.save(application);

        CandidateHorseProfile snapshot = new CandidateHorseProfile();
        snapshot.setAdmissionId(application.getId());
        snapshot.setName(request.name().trim());
        snapshot.setBreed(trimNullable(request.breed()));
        snapshot.setDateOfBirth(request.dateOfBirth());
        snapshot.setRegistrationNumber(ueln(request.registrationNumber()));
        snapshot.setRegistryName(trimNullable(request.registryName()));
        snapshot.setSireName(trimNullable(request.sireName()));
        snapshot.setSireRegistrationNumber(ueln(request.sireRegistrationNumber()));
        snapshot.setDamName(trimNullable(request.damName()));
        snapshot.setDamRegistrationNumber(ueln(request.damRegistrationNumber()));
        snapshot.setPedigreeNotes(trimNullable(request.pedigreeNotes()));
        candidates.save(snapshot);

        List<AdmissionDocument> savedDocuments = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            AdmissionDocumentMetadataRequest item = metadata.get(i);

            String key = fileStorage.store(file, admissionFolder(application.getId()));
            storedKeys.add(key);

            AdmissionDocument document = new AdmissionDocument();
            document.setAdmissionId(application.getId());
            document.setDocumentType(item.documentType());
            document.setFileUrl(key);
            document.setOriginalFileName(
                    safeOriginalFileName(file.getOriginalFilename())
            );
            document.setRecordDate(item.recordDate());
            document.setNote(trimNullable(item.note()));

            savedDocuments.add(documents.save(document));
        }
        return toDetail(application, snapshot, savedDocuments);
    }

    @Transactional(readOnly = true)
    public List<AdmissionSummaryResponse> getMyAdmissions(Long ownerId, AdmissionStatus status) {
        return admissions.findByOwnerIdOrderBySubmittedAtDesc(ownerId).stream()
                .filter(admission -> status == null || admission.getStatus() == status)
                .map(admission -> {
                    CandidateHorseProfile candidate = findCandidate(admission.getId());
                    String imageUrl = documents.findByAdmissionId(admission.getId()).stream()
                            .filter(d -> com.rtms.backend.admission.enums.AdmissionDocumentType.HORSE_PHOTO.equals(d.getDocumentType()))
                            .findFirst()
                            .map(this::documentUrl)
                            .orElse(null);
                    return new AdmissionSummaryResponse(admission.getId(), admission.getStatus(),
                            candidate.getName(), candidate.getBreed(), candidate.getDateOfBirth(),
                            admission.getSubmittedAt(), admission.getQuarantineStallId(), null, imageUrl);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OwnerAdmissionDetailResponse getMyAdmission(Long ownerId, Long admissionId) {
        AdmissionApplication application = ownedAdmission(ownerId, admissionId);
        return toDetail(application, findCandidate(admissionId), documents.findByAdmissionId(admissionId));
    }

    @Transactional(readOnly = true)
    public List<AdmissionDocumentResponse> getMyDocuments(Long ownerId, Long admissionId) {
        ownedAdmission(ownerId, admissionId);
        return documents.findByAdmissionId(admissionId).stream().map(this::toDocumentResponse).toList();
    }

    @Transactional
    public AdmissionDocumentResponse upload(Long ownerId, Long admissionId,
            AdmissionDocumentType type, LocalDate recordDate, String note, MultipartFile file) {
        AdmissionApplication application = admissions.findByIdForUpdate(admissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        assertOwner(ownerId, application);
        // No document changes after Groom has claimed/started reviewing this application.
        if (application.getStatus() != AdmissionStatus.GROOM_REVIEW
                || application.getGroomId() != null
                || application.getGroomDecision() != null
                || application.getGroomReviewedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Admission documents are locked after Groom starts review");
        }
        if (note != null && note.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document note is too long");
        }
        String key = fileStorage.store(file, admissionFolder(admissionId));
        try {
            AdmissionDocument document = new AdmissionDocument();
            document.setAdmissionId(admissionId);
            document.setDocumentType(type);
            document.setFileUrl(key);
            document.setOriginalFileName(safeOriginalFileName(file.getOriginalFilename()));
            document.setRecordDate(recordDate);
            document.setNote(trimNullable(note));
            return toDocumentResponse(documents.save(document));
        } catch (RuntimeException ex) {
            fileStorage.delete(key);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public AdmissionDocument getDocumentForViewer(Long admissionId, Long documentId,
            AuthenticatedUser viewer) {
        AdmissionApplication application = admissions.findById(admissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        if ("HORSE_OWNER".equals(viewer.getRole())) {
            assertOwner(viewer.getUserId(), application);
        } else if (!"VETERINARIAN".equals(viewer.getRole())) {
            assertViewerCanRead(admissionId, viewer);
        }
        AdmissionDocument document = documents.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!admissionId.equals(document.getAdmissionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public void assertViewerCanRead(Long admissionId, AuthenticatedUser viewer) {
        AdmissionApplication admission = admissions.findById(admissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        boolean allowed = switch (viewer.getRole()) {
            case "CLUB_MANAGER" -> true;
            case "HORSE_OWNER" -> java.util.Objects.equals(admission.getOwnerId(), viewer.getUserId());
            case "HEAD_TRAINER" -> java.util.Objects.equals(admission.getTrainerId(), viewer.getUserId());
            case "GROOM" -> java.util.Objects.equals(admission.getGroomId(), viewer.getUserId())
                    || admission.getStatus() == AdmissionStatus.GROOM_REVIEW;
            default -> false;
        };
        if (!allowed) throw new org.springframework.security.access.AccessDeniedException("Admission is not assigned to you");
    }

    /** Where a client loads this document from: its cloud URL, or this API when it is on local disk. */
    private String documentUrl(AdmissionDocument document) {
        return fileStorage.publicUrl(document.getFileUrl(),
                "/api/admissions/" + document.getAdmissionId() + "/documents/" + document.getId() + "/file");
    }

    /** Storage folder that groups every document of one admission. */
    private static String admissionFolder(Long admissionId) {
        return "rtms/admissions/" + admissionId;
    }

    public AdmissionDocumentResponse toDocumentResponse(AdmissionDocument document) {
        return new AdmissionDocumentResponse(document.getId(), document.getDocumentType(),
                documentUrl(document), document.getOriginalFileName(), document.getRecordDate(), document.getNote(),
                document.getUploadedAt(), MEDICAL_TYPES.contains(document.getDocumentType()));
    }

    private OwnerAdmissionDetailResponse toDetail(AdmissionApplication a, CandidateHorseProfile c,
            List<AdmissionDocument> docs) {
        var candidate = new OwnerAdmissionDetailResponse.CandidateSnapshot(c.getName(), c.getBreed(),
                c.getDateOfBirth(), c.getRegistrationNumber(), c.getRegistryName(),
                c.getSireName(), c.getSireRegistrationNumber(),
                c.getDamName(), c.getDamRegistrationNumber(), c.getPedigreeNotes());
        return new OwnerAdmissionDetailResponse(a.getId(), a.getStatus(), a.getSubmittedAt(),
                candidate, docs.stream().map(this::toDocumentResponse).toList(),
                a.getGroomFeedback(), a.getVetFeedback(), a.getTrainerFeedback(), a.getManagerFeedback());
    }

    private CandidateHorseProfile findCandidate(Long admissionId) {
        return candidates.findByAdmissionId(admissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate snapshot not found"));
    }

    private AdmissionApplication ownedAdmission(Long ownerId, Long admissionId) {
        AdmissionApplication application = admissions.findById(admissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        assertOwner(ownerId, application);
        return application;
    }

    private void assertOwner(Long ownerId, AdmissionApplication application) {
        if (!application.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("This admission belongs to another owner");
        }
    }

    private static String trimNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String ueln(String value) {
        String normalized = trimNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private void validateDocumentMetadata(
            List<AdmissionDocumentMetadataRequest> metadata,
            List<MultipartFile> files) {
        if (metadata == null || files == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document metadata and files are required"
            );
        }

        if (metadata.size() != files.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document metadata count must match file count"
            );
        }

        if (metadata.size() < 4 || metadata.size() > 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Submit between 4 and 8 documents"
            );
        }

        Set<AdmissionDocumentType> selectedTypes =
                EnumSet.noneOf(AdmissionDocumentType.class);

        for (AdmissionDocumentMetadataRequest item : metadata) {
            if (item == null || item.documentType() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Document type is required"
                );
            }

            if (!selectedTypes.add(item.documentType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate document type" + item.documentType()
                );
            }

            if (item.note() != null && item.note().length() > 2000) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Document note must not exceed 2000 characters"
                );
            }
        }

            if (!selectedTypes.containsAll(REQUIRED_DOCUMENT_TYPES)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Horse photo, registration document, pedigree certificate "
                                + "and vaccination record are required"
                );
            }
    }


    private List<String> registerFileCleanup() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "File cleanup requires an active transaction"
            );
        }

        List<String> storedKeys = new ArrayList<>();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            for (String key : storedKeys) {
                                fileStorage.delete(key);
                            }
                        } else if (status == STATUS_UNKNOWN) {
                            log.error(
                                "Admission transaction outcome unknown; "
                                + "check stored files: {}",
                                storedKeys
                            );
                        }
                    }
                }
                );
        return storedKeys;
    }

    private static String safeOriginalFileName(String filename) {
        if (filename == null || filename.isBlank()) return null;
        String normalized = filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (basename.isEmpty()) return null;
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }

    private void validateFiles(
            List<AdmissionDocumentMetadataRequest> metadata,
            List<MultipartFile> files) {
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            AdmissionDocumentType type = metadata.get(i).documentType();

            fileStorage.validate(file);

            String mediaType = file.getContentType().toLowerCase(Locale.ROOT);

            if (type == AdmissionDocumentType.HORSE_PHOTO && !mediaType.startsWith("image/")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Horse photo must be JPEG, PNG, or WebP"
                );
            }
        }
    }
}
