package com.rtms.backend.admission.controller;

import com.rtms.backend.admission.entity.AdmissionDocument;
import com.rtms.backend.admission.enums.AdmissionDocumentType;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.common.storage.FileStorageService;
import com.rtms.backend.admission.service.AdmissionQueryService;
import com.rtms.backend.admission.service.OwnerAdmissionService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admissions")
public class AdmissionDocumentDownloadController {
    private final OwnerAdmissionService service;
    private final FileStorageService storage;
    private final AdmissionQueryService admissionQueryService;

    public AdmissionDocumentDownloadController(OwnerAdmissionService service, FileStorageService storage,
                                                AdmissionQueryService admissionQueryService) {
        this.service = service;
        this.storage = storage;
        this.admissionQueryService = admissionQueryService;
    }

    @GetMapping("/{id}/documents/{documentId}/file")
    @PreAuthorize("hasAuthority('ADMISSION_DOCUMENT_VIEW')")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        if ("VETERINARIAN".equals(viewer.getRole())) {
            admissionQueryService.assertVetAssignedOrManager(id, viewer);
        }
        AdmissionDocument document = service.getDocumentForViewer(id, documentId, viewer);
        // Local and cloud files are both streamed from here, so the checks above always apply.
        Resource file = storage.load(document.getFileUrl());
        String filename = document.getOriginalFileName() == null
                ? "admission-document-" + documentId
                : document.getOriginalFileName();
        boolean isHorsePhoto = document.getDocumentType() == AdmissionDocumentType.HORSE_PHOTO;
        ContentDisposition disposition = isHorsePhoto
                ? ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(storage.mediaType(document.getFileUrl()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }
}
