package com.rtms.backend.stable.service;
import com.rtms.backend.horse.service.HorseService;
import com.rtms.backend.training.service.TrainingWorkoutService;


import com.rtms.backend.config.FarmSchedulePolicy;
import com.rtms.backend.stable.entity.StableStall;
import com.rtms.backend.identity.entity.User;
import com.rtms.backend.horse.repository.HorseRepository;
import com.rtms.backend.stable.repository.StableStallRepository;
import com.rtms.backend.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StableStallService {

    private final StableStallRepository stableStallRepository;
    private final UserRepository userRepository;
    private final HorseRepository horseRepository;
    private final TrainingWorkoutService workoutService;

    /**
     * @param workoutService dùng để đồng bộ Groom cho các buổi tập tương lai.
     *                       (used to sync the groom of future workouts.)
     */
    public StableStallService(StableStallRepository stableStallRepository,
                              UserRepository userRepository,
                              HorseRepository horseRepository,
                              TrainingWorkoutService workoutService) {
        this.stableStallRepository = stableStallRepository;
        this.userRepository = userRepository;
        this.horseRepository = horseRepository;
        this.workoutService = workoutService;
    }

    /**
     * BR-06 — Mỗi Groom tối đa 3 chuồng.
     *
     * Căn cứ thực tế: nhân viên chuồng trại ở lò đua thường chăm 2–3 con mỗi
     * ngày. Con số này cũng ăn khớp với số lot mỗi sáng (2–3 lot trong khung
     * 06:00–10:00): nếu cả 3 con cùng tập một ngày, chúng vừa đủ xếp vào 3 lot
     * khác nhau mà không vi phạm BR-09 (một Groom không dắt 2 ngựa cùng lot).
     */
    @Transactional
    public StableStall assignGroom(Long stallId, Long groomId) {
        StableStall stall = stableStallRepository.findById(stallId)
                .orElseThrow(() -> new RuntimeException("Stall #" + stallId + " not found"));

        // TH1: gỡ Groom khỏi chuồng -> không cần kiểm tra BR-06/BR-09,
        // nhưng vẫn phải đồng bộ buổi tập: chúng thành "chưa phân công" để
        // Trainer thấy mà xử lý, thay vì treo ở người không còn chăm con ngựa.
        if (groomId == null) {
            horseRepository.findByCurrentStallId(stallId)
                    .ifPresent(h -> workoutService.reassignFutureWorkoutsToGroom(h.getId(), null));

            stall.setGroomId(null);
            return stableStallRepository.save(stall);
        }

        // TH2: gán lại ĐÚNG Groom đang giữ chuồng này -> không tính thêm 1
        if (groomId.equals(stall.getGroomId())) {
            return stall;
        }

        // TH3: kiểm tra người được gán đúng là GROOM
        User user = userRepository.findById(groomId)
                .orElseThrow(() -> new RuntimeException("User #" + groomId + " not found"));

        if (user.getRole() == null || !"GROOM".equalsIgnoreCase(user.getRole().getName())) {
            throw new IllegalArgumentException(String.format(
                    "User '%s' has the role %s, not GROOM, and cannot be assigned to a stall",
                    user.getFullName(),
                    user.getRole() != null ? user.getRole().getName() : "UNKNOWN"));
        }

        // TH4: BR-06
        long assignedCount = stableStallRepository.countByGroomId(groomId);
        if (assignedCount >= FarmSchedulePolicy.MAX_STALLS_PER_GROOM) {
            throw new IllegalStateException(String.format(
                    "Groom '%s' already looks after %d stalls. A groom may have at most %d stalls, "
                  + "to protect the quality of care and the horses' welfare",
                    user.getFullName(), assignedCount, FarmSchedulePolicy.MAX_STALLS_PER_GROOM));
        }

        // TH5: đồng bộ buổi tập tương lai của con ngựa đang ở chuồng này.
        //
        // PHẢI gọi TRƯỚC stall.setGroomId(). Nếu vướng BR-09 thì hàm này ném
        // lỗi, @Transactional rollback, và chuồng giữ nguyên Groom cũ. Ghi
        // trước rồi kiểm sau sẽ để lại trạng thái nửa vời nếu ai đó lỡ bỏ
        // @Transactional khỏi phương thức này.
        horseRepository.findByCurrentStallId(stallId)
                .ifPresent(h -> workoutService.reassignFutureWorkoutsToGroom(h.getId(), groomId));

        stall.setGroomId(groomId);
        return stableStallRepository.save(stall);
    }
}
