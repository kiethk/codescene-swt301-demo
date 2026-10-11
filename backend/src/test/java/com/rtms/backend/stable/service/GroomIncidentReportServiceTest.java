package com.rtms.backend.stable.service;
import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.service.CareScheduleService;
import com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.stable.dto.HandleIncidentRequest;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroomIncidentReportServiceTest {

    @Mock
    private GroomIncidentReportRepository incidentReportRepository;

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private FileStorageService fileStorage;

    @Mock
    private com.rtms.backend.medical.repository.CareScheduleRepository careScheduleRepository;

    @Mock
    private CareScheduleService careScheduleService;

    @Mock
    private TrainingDecisionService trainingDecisionService;

    private GroomIncidentReportService incidentReportService;

    @BeforeEach
    void setUp() {
        incidentReportService = new GroomIncidentReportService(
                incidentReportRepository, horseRepository, fileStorage,
                careScheduleRepository,
                careScheduleService,
                trainingDecisionService);
    }

    @Test
    @DisplayName("Vòng đời: REPORTED -> IN_REVIEW (Vet tiếp nhận sự cố)")
    void testHandleReport_ReportedToInReview_Success() {
        GroomIncidentReport report = new GroomIncidentReport();
        report.setId(1L);
        report.setStatus(IncidentStatus.REPORTED);

        when(incidentReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(incidentReportRepository.save(any(GroomIncidentReport.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticatedUser vetUser = new AuthenticatedUser(100L, "vet@example.com", "VETERINARIAN");

        HandleIncidentRequest req = new HandleIncidentRequest();
        req.setStatus(IncidentStatus.IN_REVIEW);
        req.setHandlerNote("Đang trên đường đến chuồng kiểm tra");

        GroomIncidentReport result = incidentReportService.handleReport(1L, req, vetUser);

        assertEquals(IncidentStatus.IN_REVIEW, result.getStatus());
        assertEquals(100L, result.getHandledById());
        assertEquals("Đang trên đường đến chuồng kiểm tra", result.getHandlerNote());
        assertNotNull(result.getHandledAt());
    }

    @Test
    @DisplayName("Vòng đời: IN_REVIEW -> RESOLVED (Khám xong, đã xử lý)")
    void testHandleReport_InReviewToResolved_Success() {
        GroomIncidentReport report = new GroomIncidentReport();
        report.setId(1L);
        report.setStatus(IncidentStatus.IN_REVIEW);

        when(incidentReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(incidentReportRepository.save(any(GroomIncidentReport.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticatedUser vetUser = new AuthenticatedUser(100L, "vet@example.com", "VETERINARIAN");

        HandleIncidentRequest req = new HandleIncidentRequest();
        req.setStatus(IncidentStatus.RESOLVED);
        req.setHandlerNote("Đã băng bó và lập bệnh án");

        GroomIncidentReport result = incidentReportService.handleReport(1L, req, vetUser);

        assertEquals(IncidentStatus.RESOLVED, result.getStatus());
        assertEquals(100L, result.getHandledById());
    }

    @Test
    @DisplayName("Vòng đời: Chặn chuyển trạng thái bất hợp lệ (REPORTED -> RESOLVED nhảy cóc)")
    void testHandleReport_InvalidTransition_ThrowsException() {
        GroomIncidentReport report = new GroomIncidentReport();
        report.setId(1L);
        report.setStatus(IncidentStatus.REPORTED);

        when(incidentReportRepository.findById(1L)).thenReturn(Optional.of(report));

        AuthenticatedUser vetUser = new AuthenticatedUser(100L, "vet@example.com", "VETERINARIAN");

        HandleIncidentRequest req = new HandleIncidentRequest();
        req.setStatus(IncidentStatus.RESOLVED); // Nhảy cóc mà chưa qua IN_REVIEW

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentReportService.handleReport(1L, req, vetUser));

        assertTrue(ex.getMessage().contains("Không thể chuyển báo cáo từ REPORTED sang RESOLVED"));
        verify(incidentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lọc danh sách báo cáo theo trạng thái IncidentStatus")
    void testGetReports_FilterByStatus() {
        GroomIncidentReport r1 = new GroomIncidentReport();
        r1.setStatus(IncidentStatus.REPORTED);

        GroomIncidentReport r2 = new GroomIncidentReport();
        r2.setStatus(IncidentStatus.IN_REVIEW);

        when(incidentReportRepository.findAllByOrderByReportedAtDesc()).thenReturn(List.of(r1, r2));

        AuthenticatedUser manager = new AuthenticatedUser(1L, "mgr@example.com", "CLUB_MANAGER");

        List<GroomIncidentReport> reportedOnly = incidentReportService.getReports(null, IncidentStatus.REPORTED, manager);
        assertEquals(1, reportedOnly.size());
        assertEquals(IncidentStatus.REPORTED, reportedOnly.get(0).getStatus());
    }

    @Test
    @DisplayName("Groom truyền horseId chỉ xem được báo cáo do chính mình tạo")
    void testGetReports_GroomRoleWithHorseId_OnlyReturnsOwnReports() {
        GroomIncidentReport r1 = new GroomIncidentReport();
        r1.setId(1L);
        r1.setHorseId(10L);
        r1.setGroomId(5L); // Groom hiện tại

        GroomIncidentReport r2 = new GroomIncidentReport();
        r2.setId(2L);
        r2.setHorseId(10L);
        r2.setGroomId(9L); // Groom khác

        when(incidentReportRepository.findByHorseId(10L)).thenReturn(List.of(r1, r2));

        AuthenticatedUser groom = new AuthenticatedUser(5L, "groom@example.com", "GROOM");

        List<GroomIncidentReport> results = incidentReportService.getReports(10L, null, groom);
        assertEquals(1, results.size());
        assertEquals(5L, results.get(0).getGroomId());
    }

    @Test
    @DisplayName("attachImage thành công: lưu ảnh mới, dọn ảnh cũ")
    void testAttachImage_Success() {
        GroomIncidentReport report = new GroomIncidentReport();
        report.setId(10L);
        report.setGroomId(5L);
        report.setImageUrl("local:old.jpg");

        when(incidentReportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(incidentReportRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        org.springframework.web.multipart.MultipartFile file =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(fileStorage.store(file, "rtms/incidents/10")).thenReturn("local:new.jpg");

        AuthenticatedUser groom = new AuthenticatedUser(5L, "groom@example.com", "GROOM");

        GroomIncidentReport updated = incidentReportService.attachImage(10L, file, groom);

        assertEquals("local:new.jpg", updated.getImageUrl());
        verify(fileStorage).delete("local:old.jpg");
    }

    @Test
    @DisplayName("attachImage: Groom khác không được đính ảnh vào báo cáo của người khác")
    void testAttachImage_WrongGroom_ThrowsAccessDenied() {
        GroomIncidentReport report = new GroomIncidentReport();
        report.setId(10L);
        report.setGroomId(5L);

        when(incidentReportRepository.findById(10L)).thenReturn(Optional.of(report));

        org.springframework.web.multipart.MultipartFile file =
                mock(org.springframework.web.multipart.MultipartFile.class);

        AuthenticatedUser otherGroom = new AuthenticatedUser(99L, "other@example.com", "GROOM");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> incidentReportService.attachImage(10L, file, otherGroom));
    }

    @Test
    @DisplayName("attachImage: từ chối file không phải ảnh (PDF)")
    void testAttachImage_NonImage_ThrowsIllegalArgument() {
        GroomIncidentReport report = new GroomIncidentReport();
        report.setId(10L);
        report.setGroomId(5L);

        when(incidentReportRepository.findById(10L)).thenReturn(Optional.of(report));

        org.springframework.web.multipart.MultipartFile file =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getContentType()).thenReturn("application/pdf");

        AuthenticatedUser groom = new AuthenticatedUser(5L, "groom@example.com", "GROOM");

        assertThrows(IllegalArgumentException.class,
                () -> incidentReportService.attachImage(10L, file, groom));
    }

    @Test
    @DisplayName("createReport: Khi report có description thực tế, schedule nhận đúng description")
    void testCreateReport_WithActualDescription_PassesDescriptionToSchedule() {
        com.rtms.backend.horse.entity.Horse horse = new com.rtms.backend.horse.entity.Horse();
        horse.setId(10L);
        when(horseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(horse));

        when(incidentReportRepository.save(any(GroomIncidentReport.class))).thenAnswer(inv -> {
            GroomIncidentReport r = inv.getArgument(0);
            if (r.getId() == null) r.setId(100L);
            return r;
        });

        when(careScheduleRepository.findFirstByHorseIdAndCareTypeAndStatusInOrderByCreatedAtDesc(eq(10L), eq(com.rtms.backend.medical.enums.CareType.URGENT), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new com.rtms.backend.medical.entity.CareSchedule()));

        com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest req = new com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest();
        req.setHorseId(10L);
        req.setTitle("Sprained ankle");
        req.setDescription("Horse slipped on wet ground during walking");

        AuthenticatedUser groom = new AuthenticatedUser(5L, "groom@example.com", "GROOM");

        GroomIncidentReport saved = incidentReportService.createReport(req, groom);
        assertNotNull(saved);

        org.mockito.ArgumentCaptor<String> descCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(careScheduleService).createSchedule(eq(10L), eq(com.rtms.backend.medical.enums.CareType.URGENT), descCaptor.capture(), isNull(), eq(100L));
        assertEquals("Horse slipped on wet ground during walking", descCaptor.getValue());
    }

    @Test
    @DisplayName("createReport: Khi description null hoặc blank, từ chối với RuntimeException")
    void testCreateReport_WithNullOrBlankDescription_ThrowsValidationException() {
        com.rtms.backend.horse.entity.Horse horse = new com.rtms.backend.horse.entity.Horse();
        horse.setId(10L);
        when(horseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(horse));

        com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest req = new com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest();
        req.setHorseId(10L);
        req.setTitle("Sudden colic symptoms");
        req.setDescription("   "); // Blank description

        AuthenticatedUser groom = new AuthenticatedUser(5L, "groom@example.com", "GROOM");

        assertThrows(RuntimeException.class, () -> incidentReportService.createReport(req, groom));
    }
}
