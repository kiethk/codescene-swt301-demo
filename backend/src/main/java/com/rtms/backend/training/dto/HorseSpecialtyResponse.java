package com.rtms.backend.training.dto;

import com.rtms.backend.training.enums.DistanceBand;
import com.rtms.backend.training.enums.SurfaceType;

import java.util.List;

/**
 * Sở trường của một chiến mã, suy ra từ các buổi tập đã hoàn thành (không lưu trong DB).
 * (The specialty of a horse, inferred from its completed workouts (not stored in the DB).)
 *
 * @param minWorkoutsPerGroup số buổi tối thiểu mỗi nhóm cần có, để giao diện hiện "2/3" mà không tự hard-code
 *                            (the minimum workouts a group needs, so the UI can show "2/3" without hard-coding it)
 * @param strongDistances     các nhóm cự ly ngựa làm tốt, rỗng nếu chưa có nhóm nào đạt; có thể nhiều nhóm
 *                            (the bands the horse does well in; empty when none qualifies; may hold several)
 * @param preferredSurface    mặt sân ưa thích, null nếu chưa rõ (the preferred surface; null when unclear)
 * @param distanceStats       thống kê của mọi nhóm cự ly có dữ liệu, kể cả nhóm chưa đủ buổi
 *                            (statistics of every band that has data, including those with too few workouts)
 */
public record HorseSpecialtyResponse(int minWorkoutsPerGroup,
                                     List<DistanceBand> strongDistances,
                                     SurfaceType preferredSurface,
                                     List<DistanceStat> distanceStats) {
}
