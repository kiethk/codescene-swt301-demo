package com.rtms.backend.training.service;

import com.rtms.backend.training.dto.CreateSubjectRequest;
import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.enums.IntensityLevel;
import com.rtms.backend.training.enums.SubjectCategory;
import com.rtms.backend.training.enums.SurfaceType;
import com.rtms.backend.training.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    private SubjectService subjectService;

    @BeforeEach
    void setUp() {
        subjectService = new SubjectService(subjectRepository);
    }

    private CreateSubjectRequest validRequest() {
        CreateSubjectRequest req = new CreateSubjectRequest();
        req.setCategory(SubjectCategory.ENDURANCE);
        req.setName("45-minute heart-rate run");
        req.setDescription("Keep a moderate pace");
        req.setSurfaceType(SurfaceType.TURF);
        req.setTargetDistanceMeters(BigDecimal.valueOf(1600));
        req.setIntensityLevel(IntensityLevel.MEDIUM);
        req.setDurationMinutes(45);
        return req;
    }

    @Test
    @DisplayName("1. Lists subjects without a category filter")
    void testGetAllSubjects_WithoutCategory_ReturnsAll() {
        Subject s1 = new Subject();
        s1.setId(1L);
        s1.setName("Warm-up");

        Subject s2 = new Subject();
        s2.setId(2L);
        s2.setName("Steady run");

        when(subjectRepository.findAll()).thenReturn(List.of(s1, s2));

        List<Subject> result = subjectService.getAllSubjects(null);

        assertEquals(2, result.size());
        verify(subjectRepository).findAll();
        verify(subjectRepository, never()).findByCategory(any());
    }

    @Test
    @DisplayName("2. Lists subjects filtered by category")
    void testGetAllSubjects_WithCategory_ReturnsFiltered() {
        Subject s = new Subject();
        s.setId(1L);
        s.setCategory(SubjectCategory.SPEED);
        s.setName("Fitness drill");

        when(subjectRepository.findByCategory(SubjectCategory.SPEED)).thenReturn(List.of(s));

        List<Subject> result = subjectService.getAllSubjects(SubjectCategory.SPEED);

        assertEquals(1, result.size());
        assertEquals("Fitness drill", result.get(0).getName());
        verify(subjectRepository).findByCategory(SubjectCategory.SPEED);
    }

    @Test
    @DisplayName("3. Gets a subject by id")
    void testGetSubjectById_Success() {
        Subject s = new Subject();
        s.setId(5L);
        s.setName("Short-distance burst");

        when(subjectRepository.findById(5L)).thenReturn(Optional.of(s));

        Subject result = subjectService.getSubjectById(5L);

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("Short-distance burst", result.getName());
    }

    @Test
    @DisplayName("4. Fails when the subject id is unknown")
    void testGetSubjectById_NotFound_ThrowsException() {
        when(subjectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> subjectService.getSubjectById(999L));
    }

    @Test
    @DisplayName("5. Creates a subject with a valid duration (15 - 240 minutes)")
    void testCreateSubject_Success() {
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
            Subject s = inv.getArgument(0);
            s.setId(88L);
            return s;
        });

        Subject created = subjectService.createSubject(validRequest());

        assertNotNull(created);
        assertEquals(88L, created.getId());
        assertEquals(SubjectCategory.ENDURANCE, created.getCategory());
        assertEquals("45-minute heart-rate run", created.getName());
        assertEquals(45, created.getDurationMinutes());
        verify(subjectRepository).save(any(Subject.class));
    }

    @Test
    @DisplayName("5b. A missing duration defaults to 60 minutes")
    void testCreateSubject_DefaultDuration() {
        CreateSubjectRequest req = validRequest();
        req.setDurationMinutes(null);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(60, subjectService.createSubject(req).getDurationMinutes());
    }

    @Test
    @DisplayName("6. A subject without a category is rejected")
    void testCreateSubject_MissingCategory_ThrowsIllegalArgument() {
        CreateSubjectRequest req = validRequest();
        req.setCategory(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> subjectService.createSubject(req));
        assertTrue(ex.getMessage().contains("Category is required"));
        verify(subjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. A duration that is too short (< 15 minutes) is rejected")
    void testCreateSubject_DurationTooShort_ThrowsIllegalArgument() {
        CreateSubjectRequest req = validRequest();
        req.setDurationMinutes(10); // < 15

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> subjectService.createSubject(req));
        assertTrue(ex.getMessage().contains("between 15 and 240 minutes"));
    }

    @Test
    @DisplayName("8. A duration that is too long (> 240 minutes) is rejected")
    void testCreateSubject_DurationTooLong_ThrowsIllegalArgument() {
        CreateSubjectRequest req = validRequest();
        req.setDurationMinutes(300); // > 240

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> subjectService.createSubject(req));
        assertTrue(ex.getMessage().contains("between 15 and 240 minutes"));
    }
}
