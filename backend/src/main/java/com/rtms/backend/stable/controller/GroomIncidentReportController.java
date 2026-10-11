package com.rtms.backend.stable.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest;
import com.rtms.backend.stable.dto.HandleIncidentRequest;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.stable.service.GroomIncidentReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groom-incident-reports")
public class GroomIncidentReportController {

    private final GroomIncidentReportService incidentReportService;

    public GroomIncidentReportController(GroomIncidentReportService incidentReportService) {
        this.incidentReportService = incidentReportService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GROOM_INCIDENT_REPORT_CREATE')")
    public ApiResponse<GroomIncidentReport> createReport(
            @RequestBody CreateGroomIncidentReportRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        GroomIncidentReport created = incidentReportService.createReport(request, currentUser);
        return ApiResponse.success(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GROOM_INCIDENT_REPORT_VIEW')")
    public ApiResponse<List<GroomIncidentReport>> getReports(
            @RequestParam(required = false) Long horseId,
            @RequestParam(required = false) IncidentStatus status,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(
                incidentReportService.getReports(horseId, status, currentUser));
    }

    /**
     * Thú y tiếp nhận hoặc kết luận sự cố.
     * Dashboard Thú y gọi GET /api/groom-incident-reports?status=REPORTED
     * để lấy danh sách chờ khám, rồi gọi endpoint này.
     */
    @PatchMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('GROOM_INCIDENT_REPORT_HANDLE')")
    public ApiResponse<GroomIncidentReport> handleReport(
            @PathVariable Long id,
            @RequestBody HandleIncidentRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(
                incidentReportService.handleReport(id, request, currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GROOM_INCIDENT_REPORT_VIEW')")
    public ApiResponse<GroomIncidentReport> getReportById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        GroomIncidentReport report = incidentReportService.getReportById(id, currentUser);
        return ApiResponse.success(report);
    }

    /**
     * Đính ảnh vào báo cáo đã tạo.
     * Dùng lại quyền GROOM_INCIDENT_REPORT_CREATE — không cần migration.
     */
    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('GROOM_INCIDENT_REPORT_CREATE')")
    public ApiResponse<GroomIncidentReport> uploadImage(
            @PathVariable Long id,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.success(
                incidentReportService.attachImage(id, file, currentUser));
    }

    /**
     * Trả file ảnh inline để thẻ <img> hiển thị được ngay.
     */
    @GetMapping("/{id}/image")
    @PreAuthorize("hasAuthority('GROOM_INCIDENT_REPORT_VIEW')")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> getImage(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        org.springframework.core.io.Resource file = incidentReportService.loadImage(id, currentUser);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(incidentReportService.imageMediaType(id, currentUser))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=incident-" + id)
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }
}

