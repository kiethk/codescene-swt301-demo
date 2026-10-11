package com.rtms.backend.medical.service;
import com.rtms.backend.admission.repository.AdmissionApplicationRepository;
import com.rtms.backend.audit.repository.AuditLogRepository;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.identity.entity.Role;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.identity.repository.UserRepository;
import com.rtms.backend.identity.repository.VeterinarianProfileRepository;
import com.rtms.backend.medical.entity.CareSchedule;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.medical.repository.HealthRecordRepository;
import com.rtms.backend.medical.repository.HorseHealthMetricRepository;
import com.rtms.backend.notification.entity.Notification;
import com.rtms.backend.notification.repository.NotificationRepository;
import com.rtms.backend.notification.service.NotificationService;
import com.rtms.backend.stable.repository.GroomIncidentReportRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.training.service.TrainingWorkoutService;
import com.rtms.backend.training.service.TrainingDecisionService;


import com.rtms.backend.config.ApiException;
import com.rtms.backend.medical.dto.CancelCareScheduleRequest;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.enums.CareType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentEnforcementTest {

    @Mock
    private CareScheduleRepository careScheduleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HorseRepository horseRepository;

    @Mock
    private HealthRecordRepository healthRecordRepository;

    @Mock
    private HorseHealthMetricRepository metricRepository;

    @Mock
    private AdmissionApplicationRepository admissionRepository;

    @Mock
    private StableStallRepository stallRepository;

    @Mock
    private GroomIncidentReportRepository incidentRepository;

    @Mock
    private VeterinarianProfileRepository vetProfileRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EntityManager entityManager;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TrainingWorkoutService workoutService;

    private CareScheduleService careScheduleService;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);

        careScheduleService = new CareScheduleService(
                careScheduleRepository,
                horseRepository,
                healthRecordRepository,
                metricRepository,
                admissionRepository,
                stallRepository,
                userRepository,
                incidentRepository,
                vetProfileRepository,
                auditLogRepository,
                eventPublisher,
                entityManager,
                notificationService,
                new TrainingDecisionService(horseRepository, careScheduleRepository, workoutService)
        );
    }

    @Test
    @DisplayName("7 & 9. Vet cố tình hủy/từ chối CareSchedule: nhận 403 Forbidden")
    void vet_cannotCancelSchedule_throwsForbidden() {
        Long scheduleId = 100L;
        Long vetId = 15L;

        CareSchedule schedule = new CareSchedule();
        schedule.setId(scheduleId);
        schedule.setVeterinarianId(vetId);
        schedule.setStatus(CareScheduleStatus.SCHEDULED);
        schedule.setCareType(CareType.INITIAL);
        schedule.setAdmissionId(1L);

        User vetUser = new User();
        vetUser.setId(vetId);
        Role vetRole = new Role();
        vetRole.setName("VETERINARIAN");
        vetUser.setRole(vetRole);

        when(careScheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(userRepository.findById(vetId)).thenReturn(Optional.of(vetUser));

        CancelCareScheduleRequest req = new CancelCareScheduleRequest();
        req.setReason("Tôi không muốn nhận ca này");

        ApiException ex = assertThrows(ApiException.class,
                () -> careScheduleService.cancelCareSchedule(scheduleId, req, vetId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("veterinarians cannot decline assignments"));
        assertEquals(CareScheduleStatus.SCHEDULED, schedule.getStatus(), "Status không được đổi sang CANCELLED");
    }

    @Test
    @DisplayName("13. Club Manager có quyền hủy/quản lý schedule khi cần thiết")
    void manager_canCancelSchedule() {
        Long scheduleId = 100L;
        Long managerId = 99L;

        CareSchedule schedule = new CareSchedule();
        schedule.setId(scheduleId);
        schedule.setVeterinarianId(15L);
        schedule.setStatus(CareScheduleStatus.SCHEDULED);
        schedule.setCareType(CareType.INITIAL);

        User managerUser = new User();
        managerUser.setId(managerId);
        Role managerRole = new Role();
        managerRole.setName("CLUB_MANAGER");
        managerUser.setRole(managerRole);

        when(careScheduleRepository.findByIdForUpdate(scheduleId)).thenReturn(Optional.of(schedule));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(careScheduleRepository.save(any(CareSchedule.class))).thenAnswer(i -> i.getArgument(0));

        CancelCareScheduleRequest req = new CancelCareScheduleRequest();
        req.setReason("Lịch kiểm tra bị hoãn do sự cố kỹ thuật");

        var response = careScheduleService.cancelCareSchedule(scheduleId, req, managerId);

        assertEquals(CareScheduleStatus.CANCELLED, response.status());
        assertEquals("Lịch kiểm tra bị hoãn do sự cố kỹ thuật", schedule.getCancelReason());
    }

    @Test
    @DisplayName("4, 5, 6. Notification: tạo mới thành công và retry không tạo notification trùng (deduplication)")
    void notification_idempotency_preventsDuplicate() {
        Long vetId = 15L;
        Long admissionId = 1L;
        Long horseId = 20L;
        String type = "VET_INITIAL_EXAM";
        String expectedKey = "15:ADMISSION_1:VET_INITIAL_EXAM";

        // Lần 1: Chưa có notification trong DB
        when(notificationRepository.existsByDeduplicationKey(expectedKey)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        Optional<Notification> firstResult = notificationService.sendAssignmentNotification(
                vetId, admissionId, horseId, type, "New horse assignment", "Exam assigned");

        assertTrue(firstResult.isPresent());
        assertEquals(expectedKey, firstResult.get().getDeduplicationKey());
        verify(notificationRepository, times(1)).save(any(Notification.class));

        // Lần 2 (Retry): Đã tồn tại key trong DB -> không save thêm, trả về notification cũ
        when(notificationRepository.existsByDeduplicationKey(expectedKey)).thenReturn(true);
        when(notificationRepository.findByDeduplicationKey(expectedKey)).thenReturn(firstResult);

        Optional<Notification> secondResult = notificationService.sendAssignmentNotification(
                vetId, admissionId, horseId, type, "New horse assignment", "Exam assigned");

        assertTrue(secondResult.isPresent());
        assertEquals(1L, secondResult.get().getId());
        // Verify save vẫn chỉ được gọi 1 lần từ lần đầu
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }
}
