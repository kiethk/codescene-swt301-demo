package com.rtms.backend.training.repository;

import com.rtms.backend.training.entity.RacingReadinessAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RacingReadinessAssessmentRepository
        extends JpaRepository<RacingReadinessAssessment, Long> {

    Optional<RacingReadinessAssessment> findByAdmissionId(Long admissionId);


    /** Toàn bộ lịch sử của một chiến mã, mới nhất trước. (The whole history of a horse, newest first.) */
    List<RacingReadinessAssessment> findByHorseIdOrderByAssessmentDateDesc(Long horseId);

    /**
     * CHỈ đánh giá định kỳ — dùng cho biểu đồ tiến bộ.
     * Bỏ mốc nhập học vì fitness lúc đó là ước lượng bằng mắt, không cùng
     * thang với điểm đo được sau khi tập.
     * (ONLY periodic assessments, used for the progress chart. The admission milestone is left out because the fitness then is
     *  estimated by eye and is not on the same scale as the scores measured after training.)
     */
    List<RacingReadinessAssessment>
        findByHorseIdAndAdmissionIdIsNullOrderByAssessmentDateAsc(Long horseId);
}