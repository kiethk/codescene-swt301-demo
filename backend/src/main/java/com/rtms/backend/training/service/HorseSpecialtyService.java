package com.rtms.backend.training.service;

import com.rtms.backend.security.AuthenticatedUser;
import com.rtms.backend.training.dto.DistanceStat;
import com.rtms.backend.training.dto.HorseSpecialtyResponse;
import com.rtms.backend.training.dto.SpecialtyWorkoutRow;
import com.rtms.backend.training.enums.DistanceBand;
import com.rtms.backend.training.enums.SurfaceType;
import com.rtms.backend.training.repository.TrainingWorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Suy ra sở trường thi đấu của chiến mã từ các buổi tập đã hoàn thành. Kết quả được tính khi đọc, không lưu bảng.
 * (Infers the racing specialty of a horse from its completed workouts. The result is computed on read and never stored.)
 *
 * Luật (The rules):
 *  - Chỉ đếm buổi COMPLETED có điểm phong độ, có cự ly, không thuộc nhóm RECOVERY, trong WINDOW_DAYS ngày gần nhất.
 *    (Only COMPLETED workouts with a rating and a distance, not in the RECOVERY category, within the last WINDOW_DAYS days.)
 *  - Cự ly: mỗi nhóm có ít nhất MIN_WORKOUTS buổi và điểm trung bình >= MIN_RATING thì ngựa có huy hiệu nhóm đó.
 *    Có thể nhiều huy hiệu.
 *    (Distance: a band with at least MIN_WORKOUTS workouts and an average rating >= MIN_RATING earns a badge.
 *    A horse may earn several.)
 *  - Mặt sân: chỉ kết luận khi có ít nhất hai mặt sân đều đủ MIN_WORKOUTS buổi, mặt tốt nhất đạt MIN_RATING và
 *    hơn mặt kế tiếp ít nhất SURFACE_LEAD điểm. Chưa từng thử mặt sân khác thì không kết luận.
 *    (Surface: only concluded when at least two surfaces each have MIN_WORKOUTS workouts, the best reaches
 *    MIN_RATING and leads the runner-up by at least SURFACE_LEAD. No conclusion if other surfaces were never tried.)
 *  - Không có nhóm cự ly nào đủ buổi thì không gán gì cả, kể cả mặt sân. Kết quả rỗng KHÔNG có nghĩa là ngựa không
 *    có sở trường, chỉ là chưa đủ cơ sở để nói; distanceStats cho biết mỗi nhóm đang thiếu gì.
 *    (When no distance band has enough workouts nothing is assigned, surface included. An empty result does NOT
 *    mean the horse has no specialty, only that there is not enough basis to say; distanceStats shows what each
 *    band is still missing.)
 *
 * Điểm trung bình làm tròn một chữ số rồi mới so ngưỡng, nên người xem thấy 7.0 thì chắc chắn đạt.
 * (Averages are rounded to one decimal before comparing with a threshold, so a visible 7.0 always qualifies.)
 *
 * topSpeedKmh KHÔNG dùng để quyết định: mọi con ngựa đều nhanh hơn ở cự ly ngắn nên nó thiên về SPRINT.
 * (topSpeedKmh does NOT decide anything: every horse is faster over short distances, which would bias towards SPRINT.)
 */
@Service
public class HorseSpecialtyService {

    static final int WINDOW_DAYS = 90;
    static final int MIN_WORKOUTS = 3;
    static final BigDecimal MIN_RATING = new BigDecimal("7.0");
    static final BigDecimal SURFACE_LEAD = new BigDecimal("1.0");

    private final TrainingWorkoutRepository workoutRepository;
    private final HorseViewAccessService accessService;

    public HorseSpecialtyService(TrainingWorkoutRepository workoutRepository,
                                 HorseViewAccessService accessService) {
        this.workoutRepository = workoutRepository;
        this.accessService = accessService;
    }

    /** Sở trường của một chiến mã, có kiểm quyền xem. (The specialty of one horse, with the view check.) */
    @Transactional(readOnly = true)
    public HorseSpecialtyResponse getSpecialty(Long horseId, AuthenticatedUser currentUser) {
        accessService.assertCanViewHorse(horseId, currentUser);
        return getSpecialties(List.of(horseId)).get(horseId);
    }

    /**
     * Sở trường của NHIỀU chiến mã bằng một truy vấn; mọi horseId đều có mặt trong kết quả. Không kiểm quyền: người gọi
     * phải tự đảm bảo các ngựa này được phép xem (ví dụ dashboard đã lọc theo khu của Trainer).
     * (The specialty of MANY horses with one query; every horseId is present in the result. No view check: the
     * caller must make sure these horses may be viewed (for example the dashboard is already filtered to the
     * trainer's areas).)
     */
    @Transactional(readOnly = true)
    public Map<Long, HorseSpecialtyResponse> getSpecialties(Collection<Long> horseIds) {
        if (horseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SpecialtyWorkoutRow>> rowsByHorse = workoutRepository
                .findSpecialtyWorkouts(horseIds, LocalDate.now().minusDays(WINDOW_DAYS)).stream()
                .collect(Collectors.groupingBy(SpecialtyWorkoutRow::horseId));

        Map<Long, HorseSpecialtyResponse> result = new HashMap<>();
        for (Long horseId : horseIds) {
            result.put(horseId, evaluate(rowsByHorse.getOrDefault(horseId, List.of())));
        }
        return result;
    }

    HorseSpecialtyResponse evaluate(List<SpecialtyWorkoutRow> rows) {
        List<DistanceStat> stats = distanceStats(rows);
        boolean anyBandJudged = stats.stream().anyMatch(stat -> stat.workoutCount() >= MIN_WORKOUTS);
        if (!anyBandJudged) {
            return new HorseSpecialtyResponse(MIN_WORKOUTS, List.of(), null, stats);
        }

        List<DistanceBand> strongDistances = stats.stream()
                .filter(stat -> stat.workoutCount() >= MIN_WORKOUTS && stat.averageRating().compareTo(MIN_RATING) >= 0)
                .map(DistanceStat::band)
                .toList();
        return new HorseSpecialtyResponse(MIN_WORKOUTS, strongDistances, preferredSurface(rows), stats);
    }

    // =================================================================
    // CỰ LY (Distance)
    // =================================================================

    /** Thống kê theo nhóm cự ly, từ ngắn đến dài. (Statistics per distance band, shortest first.) */
    private List<DistanceStat> distanceStats(List<SpecialtyWorkoutRow> rows) {
        Map<DistanceBand, List<SpecialtyWorkoutRow>> byBand = rows.stream()
                .collect(Collectors.groupingBy(row -> DistanceBand.of(row.distanceMeters()),
                        () -> new EnumMap<>(DistanceBand.class), Collectors.toList()));

        return byBand.entrySet().stream()
                .map(entry -> new DistanceStat(entry.getKey(), entry.getValue().size(),
                        averageRating(entry.getValue()), averageTopSpeed(entry.getValue())))
                .toList();
    }

    private BigDecimal averageTopSpeed(List<SpecialtyWorkoutRow> rows) {
        List<BigDecimal> speeds = rows.stream()
                .map(SpecialtyWorkoutRow::topSpeedKmh)
                .filter(Objects::nonNull)
                .toList();
        if (speeds.isEmpty()) {
            return null;
        }
        return speeds.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(speeds.size()), 1, RoundingMode.HALF_UP);
    }

    // =================================================================
    // MẶT SÂN (Surface)
    // =================================================================

    /** Mặt sân ưa thích, hoặc null nếu chưa đủ cơ sở để kết luận. (The preferred surface, or null if unclear.) */
    private SurfaceType preferredSurface(List<SpecialtyWorkoutRow> rows) {
        Map<SurfaceType, BigDecimal> averageBySurface = rows.stream()
                .collect(Collectors.groupingBy(SpecialtyWorkoutRow::surfaceType))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_WORKOUTS)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> averageRating(entry.getValue())));
        if (averageBySurface.size() < 2) {
            return null;
        }

        List<Map.Entry<SurfaceType, BigDecimal>> ranked = averageBySurface.entrySet().stream()
                .sorted(Map.Entry.<SurfaceType, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .toList();
        Map.Entry<SurfaceType, BigDecimal> best = ranked.get(0);
        BigDecimal lead = best.getValue().subtract(ranked.get(1).getValue());
        boolean clearWinner = best.getValue().compareTo(MIN_RATING) >= 0 && lead.compareTo(SURFACE_LEAD) >= 0;
        return clearWinner ? best.getKey() : null;
    }

    // =================================================================
    // DÙNG CHUNG (Shared)
    // =================================================================

    /** Điểm trung bình làm tròn một chữ số. (Average rating rounded to one decimal.) */
    private BigDecimal averageRating(List<SpecialtyWorkoutRow> rows) {
        int total = rows.stream().mapToInt(SpecialtyWorkoutRow::performanceRating).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
    }
}
