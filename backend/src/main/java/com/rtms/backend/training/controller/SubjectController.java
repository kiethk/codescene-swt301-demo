package com.rtms.backend.training.controller;

import com.rtms.backend.common.dto.ApiResponse;
import com.rtms.backend.training.dto.CreateSubjectRequest;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.enums.SubjectCategory;
import com.rtms.backend.training.service.SubjectService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PreAuthorize("hasAuthority('SUBJECT_VIEW')")
    @GetMapping
    public ApiResponse<List<Subject>> getAllSubjects(@RequestParam(required = false) SubjectCategory category) {
        return ApiResponse.success(subjectService.getAllSubjects(category));
    }

    @PreAuthorize("hasAuthority('SUBJECT_VIEW')")
    @GetMapping("/{id}")
    public ApiResponse<Subject> getSubjectById(@PathVariable Long id) {
        return ApiResponse.success(subjectService.getSubjectById(id));
    }

    @PreAuthorize("hasAuthority('SUBJECT_MANAGE')")
    @PostMapping
    public ApiResponse<Subject> createSubject(@RequestBody CreateSubjectRequest request) {
        return ApiResponse.success(subjectService.createSubject(request));
    }
}