package com.rtms.backend.training.service;

import com.rtms.backend.config.ApiException;
import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.enums.HorseStatus;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.medical.enums.CareScheduleStatus;
import com.rtms.backend.medical.repository.CareScheduleRepository;
import com.rtms.backend.training.enums.TrainingDecision;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Nơi DUY NHẤT đổi quyết định "ngựa có được tập không".
 * (The ONLY place that changes the "may the horse train" decision.)
 *
 * Trước đây 8 chỗ (Vet, Groom, Manager, sự cố...) tự đặt 3-4 trường khóa trên ngựa và không chỗ nào hủy kế
 * hoạch đang chạy: ngựa bị chặn mà buổi tập vẫn nằm trong lot, Groom vẫn được giao dắt đi tập. Gom về đây để
 * chặn tập luôn kéo theo hủy buổi tập tương lai.
 * (Before, 8 places (vet, groom, manager, incidents...) set 3-4 lock fields on the horse themselves and none
 * cancelled running plans: a horse was blocked while its workouts stayed in the lot and the groom was still told
 * to lead it out. They are gathered here so blocking always cancels future workouts.)
 */
@Service
public class TrainingDecisionService {

    private static final List<CareScheduleStatus> PENDING_EXAM_STATUSES = List.of(
            CareScheduleStatus.REQUESTED, CareScheduleStatus.SCHEDULED, CareScheduleStatus.IN_PROGRESS);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final HorseRepository horseRepository;
    private final CareScheduleRepository careScheduleRepository;
    private final TrainingWorkoutService workoutService;

    /**
     * @param workoutService hủy buổi tập tương lai khi chặn tập. TrainingWorkoutService không phụ thuộc ngược lại
     *                       service nào, nên không có vòng phụ thuộc và không cần @Lazy.
     *                       (cancels future workouts when blocking. TrainingWorkoutService never depends back on
     *                       any service, so there is no dependency cycle and no @Lazy is needed.)
     */
    public TrainingDecisionService(HorseRepository horseRepository,
                                   CareScheduleRepository careScheduleRepository,
                                   TrainingWorkoutService workoutService) {
        this.horseRepository = horseRepository;
        this.careScheduleRepository = careScheduleRepository;
        this.workoutService = workoutService;
    }

    /**
     * Chặn tập và hủy mọi buổi tập chưa diễn ra (lot rỗng bị hủy theo, plan đang chạy chuyển CANCELLED). Gọi lại
     * khi ngựa đã bị chặn là vô hại: lần hủy thứ hai không còn gì để hủy.
     * (Blocks training and cancels every workout not yet held (empty lots are cancelled too, running plans become
     * CANCELLED). Calling again on an already blocked horse is harmless: the second cancel has nothing left.)
     */
    @Transactional
    public void block(Horse horse, String reason) {
        horse.setTrainingDecision(TrainingDecision.BLOCKED);
        horse.setTrainingDecisionReason(reason);
        horseRepository.save(horse);
        workoutService.cancelFutureTrainingForHorse(horse.getId(),
                reason == null || reason.isBlank()
                        ? "Horse is blocked from training"
                        : "Horse is blocked from training: " + reason);
    }

    @Transactional
    public void allow(Horse horse) {
        horse.setTrainingDecision(TrainingDecision.ALLOWED);
        horse.setTrainingDecisionReason(null);
        horseRepository.save(horse);
    }

    /**
     * Ném lỗi kèm lý do cụ thể nếu ngựa chưa tập được. Trainer cần biết VÌ SAO và ĐẾN KHI NÀO, không chỉ "đang bị khóa".
     * (Throws an error with a concrete reason if the horse cannot train yet. The trainer needs to know WHY and
     * UNTIL WHEN, not just "locked".)
     */
    public void assertCanTrain(Horse horse) {
        if (horse.getCurrentStatus() != HorseStatus.ELIGIBLE) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_NOT_ELIGIBLE", String.format(
                    "Horse '%s' has not been admitted to the club (status %s), so a plan cannot be created yet!",
                    horse.getName(), horse.getCurrentStatus()));
        }
        if (horse.getTrainingDecision() == TrainingDecision.BLOCKED) {
            LocalDateTime nextExam = careScheduleRepository.findNextExamTime(horse.getId(), PENDING_EXAM_STATUSES);
            String reason = horse.getTrainingDecisionReason() == null || horse.getTrainingDecisionReason().isBlank()
                    ? "as directed by the vet"
                    : horse.getTrainingDecisionReason();
            String until = nextExam == null
                    ? "no follow-up exam scheduled"
                    : "re-examination at " + nextExam.format(DATE_TIME);
            throw new ApiException(HttpStatus.CONFLICT, "TRAINING_BLOCKED", String.format(
                    "Horse '%s' is resting: %s (%s). A new plan cannot be created!",
                    horse.getName(), reason, until));
        }
    }
}
