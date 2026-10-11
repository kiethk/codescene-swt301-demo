package com.rtms.backend.training.service;

import com.rtms.backend.horse.entity.Horse;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.repository.HorseTrainingPlanRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Kiểm tra một người dùng có được xem dữ liệu hiệu suất của một chiến mã hay không. Dùng chung cho biểu đồ thể lực,
 * cảnh báo và sở trường để cả ba cùng một luật.
 * (Checks whether a user may view the performance data of a horse. Shared by the fitness chart, the alerts and the
 * specialty so all three follow the same rule.)
 */
@Service
public class HorseViewAccessService {

    private final HorseRepository horseRepository;
    private final HorseTrainingPlanRepository planRepository;

    public HorseViewAccessService(HorseRepository horseRepository, HorseTrainingPlanRepository planRepository) {
        this.horseRepository = horseRepository;
        this.planRepository = planRepository;
    }

    /**
     * Owner chỉ xem ngựa của mình; Trainer chỉ xem ngựa trong khu mình phụ trách; vai khác theo quyền HORSE_VIEW.
     * (An owner sees only their own horses; a trainer only horses in their areas; other roles follow HORSE_VIEW.)
     */
    public void assertCanViewHorse(Long horseId, AuthenticatedUser currentUser) {
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new RuntimeException("Horse not found with id: " + horseId));

        if ("HORSE_OWNER".equals(currentUser.getRole())
                && !currentUser.getUserId().equals(horse.getOwnerId())) {
            throw new AccessDeniedException("You can only view horses you own");
        }
        if ("HEAD_TRAINER".equals(currentUser.getRole())
                && !planRepository.existsHorseInTrainerArea(horseId, currentUser.getUserId())) {
            throw new AccessDeniedException("You can only view horses in the areas you manage");
        }
    }
}
