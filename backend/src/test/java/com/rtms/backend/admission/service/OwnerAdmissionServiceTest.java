package com.rtms.backend.admission.service;

import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.admission.dto.AdmissionDocumentMetadataRequest;
import com.rtms.backend.admission.dto.CreateOwnerAdmissionRequest;
import com.rtms.backend.admission.entity.AdmissionApplication;
import com.rtms.backend.admission.entity.AdmissionDocument;
import com.rtms.backend.admission.entity.CandidateHorseProfile;
import com.rtms.backend.admission.enums.AdmissionDocumentType;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.admission.repository.AdmissionDocumentRepository;
import com.rtms.backend.admission.repository.CandidateHorseProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OwnerAdmissionServiceTest {
    private AdmissionApplicationRepository admissions;
    private CandidateHorseProfileRepository candidates;
    private AdmissionDocumentRepository documents;
    private FileStorageService files;
    private OwnerAdmissionService service;
    private AtomicLong sequence;

    @BeforeEach
    void setup() {
        admissions = mock(AdmissionApplicationRepository.class);
        candidates = mock(CandidateHorseProfileRepository.class);
        documents = mock(AdmissionDocumentRepository.class);
        files = mock(FileStorageService.class);
        service = new OwnerAdmissionService(admissions, candidates, documents, files);
        sequence = new AtomicLong(100);
        when(admissions.save(any())).thenAnswer(inv -> {
            AdmissionApplication app = inv.getArgument(0);
            app.setId(sequence.incrementAndGet());
            return app;
        });
        when(candidates.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateOwnerAdmissionRequest request() {
        return new CreateOwnerAdmissionRequest(
                "Pegasus", "Thoroughbred", LocalDate.of(2022, 4, 2),
                "fr1234567890123", "SIRE", "Sire", null,
                "Dam", null, "Family notes");
    }

    @Test
    void createsOneApplicationAndOneSnapshotWithoutCreatingHorse() {
        var result = service.create(7L, request());
        assertEquals(AdmissionStatus.GROOM_REVIEW, result.status());
        assertEquals("FR1234567890123", result.candidate().registrationNumber());
        assertEquals("Family notes", result.candidate().pedigreeNotes());
        assertEquals(101L, result.admissionId());
        assertTrue(result.documents().isEmpty());
        var admission = ArgumentCaptor.forClass(AdmissionApplication.class);
        verify(admissions, times(1)).save(admission.capture());
        assertEquals(7L, admission.getValue().getOwnerId());
        assertNull(admission.getValue().getHorseId());
        assertNull(admission.getValue().getQuarantineStallId());
        var candidate = ArgumentCaptor.forClass(CandidateHorseProfile.class);
        verify(candidates, times(1)).save(candidate.capture());
        assertEquals(101L, candidate.getValue().getAdmissionId());
    }

    @Test
    void submitRejectsMissingRequiredDocumentBeforeSaving() {
        List<AdmissionDocumentMetadataRequest> metadata = List.of(
                new AdmissionDocumentMetadataRequest(
                        AdmissionDocumentType.HORSE_PHOTO, null, null),
                new AdmissionDocumentMetadataRequest(
                        AdmissionDocumentType.REGISTRATION_DOCUMENT, null, null),
                new AdmissionDocumentMetadataRequest(
                        AdmissionDocumentType.PEDIGREE_CERTIFICATE, null, null),
                new AdmissionDocumentMetadataRequest(
                        AdmissionDocumentType.HEALTH_CERTIFICATE, null, null)
                );

        List<MultipartFile> uploads = List.of(
                new MockMultipartFile(
                        "files", "horse.png", "image/png", new byte[]{1}),
                new MockMultipartFile(
                        "files", "registration.pdf", "application/pdf", new byte[]{2}),
                new MockMultipartFile(
                        "files", "pedigree.pdf", "application/pdf", new byte[]{4}),
                new MockMultipartFile(
                        "files", "health.pdf", "application/pdf", new byte[]{4})
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(7L, request(), metadata, uploads)
        );

        assertEquals(400, error.getStatusCode().value());

        verifyNoInteractions(admissions, candidates, documents, files);
    }


    @Test
    void submitRejectsMetadataFileCountMismatchBeforeSaving() {
        List<AdmissionDocumentMetadataRequest> metadata = List.of(
                new AdmissionDocumentMetadataRequest(
                        AdmissionDocumentType.HORSE_PHOTO, null, null)
        );

        List<MultipartFile> uploads = List.of();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(7L, request(), metadata, uploads)
        );

        assertEquals(400, error.getStatusCode().value());
        assertEquals(
                "Document metadata count must match file count",
                error.getReason()
        );

        verifyNoInteractions(admissions, candidates, documents, files);
    }

    @Test
    void resubmittingRejectedHorseCreatesFreshApplicationAndProfileWithSameUeln() {
        var first = service.create(7L, request());
        var second = service.create(7L, request());
        assertNotEquals(first.admissionId(), second.admissionId());
        assertEquals(first.candidate().registrationNumber(), second.candidate().registrationNumber());
        verify(admissions, times(2)).save(any());
        verify(candidates, times(2)).save(any());
    }

    @Test
    void ownerCanReadOnlyOwnAdmission() {
        var existing = new AdmissionApplication();
        existing.setId(10L); existing.setOwnerId(7L);
        when(admissions.findById(10L)).thenReturn(Optional.of(existing));
        assertThrows(AccessDeniedException.class, () -> service.getMyAdmission(8L, 10L));
        verify(candidates, never()).findByAdmissionId(anyLong());
    }

    @Test
    void documentUploadRefusedOnceGroomClaimsApplication() {
        var application = new AdmissionApplication();
        application.setId(3L); application.setOwnerId(7L);
        application.setGroomId(99L);
        when(admissions.findByIdForUpdate(3L)).thenReturn(Optional.of(application));
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1,2,3});
        assertThrows(ResponseStatusException.class, () ->
                service.upload(7L, 3L, AdmissionDocumentType.HORSE_PHOTO,
                        null, null, file));
        verifyNoInteractions(files, documents);
    }

    @Test
    void uploadCannotWriteIntoSomeoneElsesAdmission() {
        var application = new AdmissionApplication();
        application.setId(3L); application.setOwnerId(7L);
        when(admissions.findByIdForUpdate(3L)).thenReturn(Optional.of(application));
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1,2,3});
        assertThrows(AccessDeniedException.class, () ->
                service.upload(8L, 3L, AdmissionDocumentType.HORSE_PHOTO,
                        null, null, file));
        verifyNoInteractions(files, documents);
    }

    @Test
    void allowedUploadCreatesDocumentForCorrectAdmission() {
        var application = new AdmissionApplication();
        application.setId(3L); application.setOwnerId(7L);
        when(admissions.findByIdForUpdate(3L)).thenReturn(Optional.of(application));
        when(files.store(any(), any())).thenReturn("local:test.jpg");
        when(documents.save(any())).thenAnswer(inv -> {
            AdmissionDocument doc = inv.getArgument(0);
            doc.setId(33L); return doc;
        });
        when(files.publicUrl(any(), any())).thenReturn("/api/admissions/3/documents/33/file");
        var file = new MockMultipartFile("file", "C:\\fakepath\\photo.jpg", "image/jpeg", new byte[]{1,2,3});
        var result = service.upload(7L, 3L, AdmissionDocumentType.HORSE_PHOTO,
                LocalDate.of(2026, 9, 1), "Arrival", file);
        assertEquals(33L, result.getId());
        assertEquals("/api/admissions/3/documents/33/file", result.getFileUrl());
        assertEquals("photo.jpg", result.getOriginalFileName());
        verify(documents, times(1)).save(argThat(d -> d.getAdmissionId() == 3L));
    }

    @Test
    void ownerListIncludesOnlyOwnAdmissions() {
        var a = new AdmissionApplication();
        a.setId(1L); a.setOwnerId(7L);
        a.setStatus(AdmissionStatus.REJECTED);
        when(admissions.findByOwnerIdOrderBySubmittedAtDesc(7L)).thenReturn(List.of(a));
        var c = new CandidateHorseProfile();
        c.setName("Pegasus");
        when(candidates.findByAdmissionId(1L)).thenReturn(Optional.of(c));
        var list = service.getMyAdmissions(7L, null);
        assertEquals(1, list.size());
        assertEquals("Pegasus", list.getFirst().getCandidateName());
        verify(admissions).findByOwnerIdOrderBySubmittedAtDesc(7L);
    }
}
