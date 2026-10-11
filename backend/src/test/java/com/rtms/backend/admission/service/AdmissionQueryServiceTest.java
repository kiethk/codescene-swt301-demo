package com.rtms.backend.admission.service;

import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.admission.dto.GroomAdmissionQueueResponse;
import com.rtms.backend.admission.dto.TrainerAdmissionQueueResponse;
import com.rtms.backend.admission.entity.AdmissionApplication;
import com.rtms.backend.admission.entity.CandidateHorseProfile;
import com.rtms.backend.admission.enums.AdmissionStatus;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.admission.repository.AdmissionDocumentRepository;
import com.rtms.backend.admission.repository.CandidateHorseProfileRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdmissionQueryServiceTest {
    private AdmissionApplicationRepository admissions;
    private CandidateHorseProfileRepository candidates;
    private AdmissionDocumentRepository documents;
    private AdmissionQueryService service;

    @BeforeEach
    void setUp() {
        admissions = mock(AdmissionApplicationRepository.class);
        candidates = mock(CandidateHorseProfileRepository.class);
        documents = mock(AdmissionDocumentRepository.class);
        service = new AdmissionQueryService(admissions,
                candidates,
                documents,
                mock(StableStallRepository.class),
                mock(HealthRecordRepository.class),
                mock(FileStorageService.class),
                mock(UserRepository.class));
    }

    @Test
    void groomQueueTrimsNameUsesInclusiveDateRangeAndCapsPageSize() {
        var page = PageRequest.of(2, 10,
                Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id")));
        when(admissions.findGroomQueue(eq(false), eq(AdmissionStatus.GROOM_REVIEW), eq(true), eq("Mercury"),
                eq(true), any(), eq(true), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        GroomAdmissionQueueResponse response = service.getGroomQueue(" Mercury ", null,
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 8), 2, 50);

        assertEquals(10, response.size());
        assertEquals(2, response.page());
        verify(admissions).findGroomQueue(eq(false), eq(AdmissionStatus.GROOM_REVIEW), eq(true), eq("Mercury"),
                eq(true), eq(LocalDateTime.of(2026, 1, 2, 0, 0)),
                eq(true), eq(LocalDateTime.of(2026, 1, 9, 0, 0)), any(Pageable.class));
    }

    @Test
    void groomQueueRejectsReversedDateRangeBeforeQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.getGroomQueue("", null,
                LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 8), 0, 10));
        verifyNoInteractions(admissions);
    }

    /**
     * Hàng chờ Trainer phải tách hai nhóm và chỉ hỏi dữ liệu của CHÍNH Trainer
     * đó — không được rơi về findAll()/findByStatus() như endpoint dùng chung
     * GET /api/admissions, vì khi đó mọi Trainer lại thấy hồ sơ của nhau.
     */
    @Test
    void trainerQueueSplitsPendingAndReviewedForThatTrainerOnly() {
        AdmissionApplication pending = admission(1L, AdmissionStatus.TRAINER_REVIEW, null);
        AdmissionApplication reviewed = admission(2L, AdmissionStatus.MANAGER_REVIEW,
                LocalDateTime.of(2026, 3, 1, 9, 0));

        when(admissions.findByTrainerIdAndStatusOrderBySubmittedAtAscIdAsc(7L, AdmissionStatus.TRAINER_REVIEW))
                .thenReturn(List.of(pending));
        when(admissions.findByTrainerIdAndTrainerReviewedAtIsNotNullOrderByTrainerReviewedAtDesc(7L))
                .thenReturn(List.of(reviewed));
        when(documents.findByAdmissionId(anyLong())).thenReturn(List.of());

        TrainerAdmissionQueueResponse response = service.getTrainerQueue(7L);

        assertEquals(1, response.pending().size());
        assertEquals(1L, response.pending().get(0).getAdmissionId());
        assertEquals(1, response.reviewed().size());
        assertEquals(2L, response.reviewed().get(0).getAdmissionId());

        verify(admissions, never()).findAll();
        verify(admissions, never()).findByStatus(any());
    }

    /** Đơn nào cũng phải có CandidateHorseProfile, nếu không toSummaryResponse ném lỗi. */
    private AdmissionApplication admission(Long id, AdmissionStatus status,
                                           LocalDateTime trainerReviewedAt) {
        AdmissionApplication a = new AdmissionApplication();
        a.setId(id);
        a.setStatus(status);
        a.setTrainerId(7L);
        a.setSubmittedAt(LocalDateTime.of(2026, 2, 1, 8, 0));
        a.setTrainerReviewedAt(trainerReviewedAt);

        CandidateHorseProfile profile = new CandidateHorseProfile();
        profile.setName("Ngựa #" + id);
        profile.setBreed("Thoroughbred");
        when(candidates.findByAdmissionId(id)).thenReturn(Optional.of(profile));

        return a;
    }
}
