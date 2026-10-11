package com.rtms.backend.stable.service;
import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.medical.service.CareScheduleService;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.stable.dto.CreateGroomIncidentReportRequest;
import com.rtms.backend.stable.dto.HandleIncidentRequest;
import com.rtms.backend.stable.entity.GroomIncidentReport;
import com.rtms.backend.stable.enums.IncidentSeverity;
import com.rtms.backend.stable.enums.IncidentStatus;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import com.rtms.backend.training.enums.TrainingDecision;
import com.rtms.backend.security.AuthenticatedUser;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class GroomIncidentReportService {

    private final GroomIncidentReportRepository incidentReportRepository;
    private final HorseRepository horseRepository;
    private final FileStorageService fileStorage;
    private final CareScheduleRepository careScheduleRepository;
    private final CareScheduleService careScheduleService;
    private final TrainingDecisionService trainingDecisionService;

    public GroomIncidentReportService(GroomIncidentReportRepository incidentReportRepository,
                                      HorseRepository horseRepository,
                                      FileStorageService fileStorage,
                                      CareScheduleRepository careScheduleRepository,
                                      CareScheduleService careScheduleService,
                                      TrainingDecisionService trainingDecisionService) {
        this.incidentReportRepository = incidentReportRepository;
        this.horseRepository = horseRepository;
        this.fileStorage = fileStorage;
        this.careScheduleRepository = careScheduleRepository;
        this.careScheduleService = careScheduleService;
        this.trainingDecisionService = trainingDecisionService;
    }

    @Transactional
    public GroomIncidentReport createReport(CreateGroomIncidentReportRequest request, AuthenticatedUser currentUser) {
        if (request.getHorseId() == null) {
            throw new RuntimeException("Horse ID is required");
        }
        Horse horse = horseRepository.findByIdForUpdate(request.getHorseId())
                .orElseThrow(() -> new RuntimeException("Horse not found with id: " + request.getHorseId()));

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Title is required");
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new RuntimeException("Description is required");
        }

        IncidentSeverity severity = request.getSeverity() != null ? request.getSeverity() : IncidentSeverity.MEDIUM;

        GroomIncidentReport report = new GroomIncidentReport(
                currentUser.getUserId(),
                request.getHorseId(),
                request.getTitle().trim(),
                request.getDescription().trim(),
                request.getImageUrl(),
                severity
        );

        GroomIncidentReport saved = incidentReportRepository.save(report);

        List<CareScheduleStatus> activeStatuses = List.of(
                CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS);
        CareSchedule active = careScheduleRepository
                .findFirstByHorseIdAndCareTypeAndStatusInOrderByCreatedAtDesc(
                        horse.getId(), CareType.URGENT, activeStatuses)
                .orElse(null);
        if (active == null) {
            String scheduleDescription = (report.getDescription() != null && !report.getDescription().trim().isEmpty())
                    ? report.getDescription().trim()
                    : "Incident reported";
            careScheduleService.createSchedule(horse.getId(), CareType.URGENT, scheduleDescription, null, saved.getId());
            active = careScheduleRepository
                    .findFirstByHorseIdAndCareTypeAndStatusInOrderByCreatedAtDesc(
                            horse.getId(), CareType.URGENT, activeStatuses)
                    .orElseThrow(() -> new IllegalStateException("Urgent schedule was not created"));
        } else {
            // Đã có ca khẩn cấp đang mở: báo cáo mới chỉ gắn vào ca đó, ngựa vẫn bị chặn tập.
            trainingDecisionService.block(horse, "Urgent veterinary care pending");
        }
        saved.setCareScheduleId(active.getId());
        return incidentReportRepository.save(saved);
    }

    public List<GroomIncidentReport> getReports(Long horseId,
                                                IncidentStatus status,
                                                AuthenticatedUser currentUser) {
        List<GroomIncidentReport> base;

        if (horseId != null) {
            base = incidentReportRepository.findByHorseId(horseId);
            if ("GROOM".equalsIgnoreCase(currentUser.getRole())) {
                base = base.stream()
                        .filter(r -> currentUser.getUserId().equals(r.getGroomId()))
                        .toList();
            }
        } else if ("GROOM".equalsIgnoreCase(currentUser.getRole())) {
            base = incidentReportRepository.findByGroomId(currentUser.getUserId());
        } else {
            base = incidentReportRepository.findAllByOrderByReportedAtDesc();
        }

        if (status == null) {
            return base;
        }
        return base.stream().filter(r -> r.getStatus() == status).toList();
    }

    /**
     * Tiếp nhận / kết luận một báo cáo sự cố.
     *
     * PHẠM VI: phần nối sang bệnh án (health_records.source_incident_id) thuộc
     * module Thú y. Ở đây ta chỉ quản lý VÒNG ĐỜI của báo cáo. Khi đồng đội làm
     * phần bệnh án, họ gọi API này với status = RESOLVED sau khi lưu bệnh án.
     */
    @Transactional
    public GroomIncidentReport handleReport(Long id,
                                            HandleIncidentRequest request,
                                            AuthenticatedUser currentUser) {
        GroomIncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident report not found with id: " + id));

        IncidentStatus next = request.getStatus();
        if (next == null) {
            throw new IllegalArgumentException("The new status is required");
        }

        IncidentStatus current = report.getStatus();
        boolean valid = switch (current) {
            case REPORTED  -> next == IncidentStatus.IN_REVIEW || next == IncidentStatus.DISMISSED;
            case IN_REVIEW -> next == IncidentStatus.RESOLVED  || next == IncidentStatus.DISMISSED;
            case RESOLVED, DISMISSED -> false;   // trạng thái kết thúc
        };

        if (!valid) {
            throw new IllegalStateException(String.format(
                    "An incident report cannot move from %s to %s", current, next));
        }

        report.setStatus(next);
        report.setHandledById(currentUser.getUserId());
        report.setHandledAt(LocalDateTime.now());
        if (request.getHandlerNote() != null && !request.getHandlerNote().isBlank()) {
            report.setHandlerNote(request.getHandlerNote().trim());
        }

        return incidentReportRepository.save(report);
    }

    /** One report. A Groom may only read a report they submitted; other roles are limited by their permission. */
    public GroomIncidentReport getReportById(Long id, AuthenticatedUser currentUser) {
        GroomIncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident report not found with id: " + id));
        if ("GROOM".equalsIgnoreCase(currentUser.getRole())
                && !currentUser.getUserId().equals(report.getGroomId())) {
            throw new AccessDeniedException("You can only view incident reports you submitted");
        }
        return report;
    }

    /**
     * Đính ảnh vào một báo cáo đã tạo.
     *
     * Vì sao tách làm HAI BƯỚC (tạo báo cáo -> rồi tải ảnh) mà không gộp:
     * cần id của báo cáo để biết ảnh thuộc về ai. Đây đúng cách luồng Owner
     * đang làm — tạo đơn rồi mới tải giấy tờ.
     *
     * Gọi lại lần nữa sẽ THAY ảnh cũ và xoá file cũ khỏi đĩa, tránh rác.
     */
    @Transactional
    public GroomIncidentReport attachImage(Long reportId,
                                           MultipartFile file,
                                           AuthenticatedUser currentUser) {

        GroomIncidentReport report = incidentReportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException(
                        "Incident report not found with id: " + reportId));

        // Chỉ người gửi báo cáo mới được đính ảnh.
        // Thú y xem được nhưng không sửa được bằng chứng của Groom.
        if ("GROOM".equalsIgnoreCase(currentUser.getRole())
                && !currentUser.getUserId().equals(report.getGroomId())) {
            throw new AccessDeniedException(
                    "You can only attach an image to a report you submitted");
        }

        // FileStorageService also accepts PDFs. Incident reports take images only,
        // so reject anything else before it is stored.
        String mediaType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!mediaType.startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Incident reports accept images only (JPEG, PNG, WebP)");
        }

        String oldKey = report.getImageUrl();
        String newKey = fileStorage.store(file, "rtms/incidents/" + reportId);   // validates size and format

        try {
            report.setImageUrl(newKey);
            GroomIncidentReport saved = incidentReportRepository.save(report);
            fileStorage.delete(oldKey);     // remove the old image only AFTER the DB write succeeds
            return saved;
        } catch (RuntimeException ex) {
            fileStorage.delete(newKey);     // DB write failed -> do not leave an orphan file
            throw ex;
        }
    }

    /** Opens the attached image from whichever storage backend holds it. */
    public Resource loadImage(Long reportId, AuthenticatedUser currentUser) {
        GroomIncidentReport report = getReportById(reportId, currentUser);
        if (report.getImageUrl() == null) {
            throw new RuntimeException("Incident report #" + reportId + " has no image attached");
        }
        return fileStorage.load(report.getImageUrl());
    }

    public MediaType imageMediaType(Long reportId, AuthenticatedUser currentUser) {
        return fileStorage.mediaType(getReportById(reportId, currentUser).getImageUrl());
    }
}

