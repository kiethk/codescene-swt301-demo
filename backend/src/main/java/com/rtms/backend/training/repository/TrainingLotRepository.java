package com.rtms.backend.training.repository;

import com.rtms.backend.training.dto.LotHorseNameRow;
import com.rtms.backend.training.dto.TrainingLotRow;
import com.rtms.backend.training.entity.TrainingLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingLotRepository extends JpaRepository<TrainingLot, Long> {

    // =================================================================
    // THUẬT TOÁN XẾP KHE (trả entity vì thuật toán cần sửa và lưu lot)
    // (SLOT ALGORITHM — returns entities because the algorithm edits and saves lots)
    // =================================================================

    /**
     * Mọi lot CÒN HIỆU LỰC của Trainer trong một ngày, sắp theo giờ tăng dần.
     * Sắp xếp là BẮT BUỘC: con trỏ quét chỉ tiến về phía trước.
     * (Every active lot of a trainer on a day, ascending by time. Ordering is REQUIRED: the
     * scanning pointer only moves forward.)
     */
    @Query("""
           SELECT l FROM TrainingLot l
           WHERE l.trainerId = :trainerId
             AND l.lotDate = :date
             AND l.status <> com.rtms.backend.training.enums.LotStatus.CANCELLED
           ORDER BY l.startTime ASC
           """)
    List<TrainingLot> findActiveLotsOfDay(@Param("trainerId") Long trainerId,
                                          @Param("date") LocalDate date);

    /**
     * BƯỚC 1 của thuật toán: các lot cùng Trainer, cùng ngày, CÙNG BÀI TẬP, giờ sớm trước.
     * (STEP 1 of the algorithm: lots of the same trainer, day and SUBJECT, earliest first.)
     */
    @Query("""
           SELECT l FROM TrainingLot l
           WHERE l.trainerId = :trainerId
             AND l.lotDate = :date
             AND l.subjectId = :subjectId
             AND l.status <> com.rtms.backend.training.enums.LotStatus.CANCELLED
           ORDER BY l.startTime ASC
           """)
    List<TrainingLot> findActiveLotsOfDayBySubject(@Param("trainerId") Long trainerId,
                                                   @Param("date") LocalDate date,
                                                   @Param("subjectId") Long subjectId);

    // =================================================================
    // API XEM LỊCH: trả thẳng DTO, số ngựa đếm bằng sub-query
    // (SCHEDULE API — returns DTO rows directly, horse count computed by a sub-query)
    // =================================================================

    /** Phần SELECT dùng chung cho mọi truy vấn xem lot. (SELECT part shared by every lot view query.) */
    String LOT_ROW_SELECT = """
            SELECT new com.rtms.backend.training.dto.TrainingLotRow(
                       l.id, l.trainerId, l.lotDate, l.startTime, l.endTime, l.subjectId, s.name,
                       s.durationMinutes, l.status, l.maxCapacity,
                       (SELECT COUNT(w) FROM TrainingWorkout w
                        WHERE w.lotId = l.id
                          AND w.status <> com.rtms.backend.training.enums.WorkoutStatus.CANCELLED))
            FROM TrainingLot l
            JOIN Subject s ON l.subjectId = s.id
            """;

    /**
     * Lịch lot của Trainer. includeCancelled = false thì ẩn lot đã hủy, vì khe giờ của nó thực tế đang TRỐNG.
     * (A trainer lot schedule. With includeCancelled = false cancelled lots are hidden, because
     * their slot is actually FREE.)
     */
    @Query(LOT_ROW_SELECT + """
            WHERE l.trainerId = :trainerId
              AND l.lotDate BETWEEN :from AND :to
              AND (:includeCancelled = true
                   OR l.status <> com.rtms.backend.training.enums.LotStatus.CANCELLED)
            ORDER BY l.lotDate ASC, l.startTime ASC
            """)
    List<TrainingLotRow> findTrainerLotRows(@Param("trainerId") Long trainerId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("includeCancelled") boolean includeCancelled);

    /**
     * Lịch lot của một GROOM: các lot mà groom này có ngựa phải dắt. Groom không sở hữu lot nào nên phải đi
     * vòng qua workout.assigned_to_id; EXISTS tránh dòng lặp mà không cần DISTINCT.
     * (A GROOM lot schedule: lots where this groom has a horse to lead. A groom owns no lot, so the query
     * goes through workout.assigned_to_id; EXISTS avoids duplicate rows without DISTINCT.)
     */
    @Query(LOT_ROW_SELECT + """
            WHERE l.lotDate BETWEEN :from AND :to
              AND l.status <> com.rtms.backend.training.enums.LotStatus.CANCELLED
              AND EXISTS (SELECT 1 FROM TrainingWorkout gw
                          WHERE gw.lotId = l.id
                            AND gw.assignedToId = :groomId
                            AND gw.status <> com.rtms.backend.training.enums.WorkoutStatus.CANCELLED)
            ORDER BY l.lotDate ASC, l.startTime ASC
            """)
    List<TrainingLotRow> findGroomLotRows(@Param("groomId") Long groomId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /**
     * Lịch lot của một OWNER: các lot có ít nhất một con ngựa CỦA OWNER ĐÓ còn hiệu lực.
     * (An OWNER lot schedule: lots with at least one active horse BELONGING TO THAT OWNER.)
     */
    @Query(LOT_ROW_SELECT + """
            WHERE l.lotDate BETWEEN :from AND :to
              AND l.status <> com.rtms.backend.training.enums.LotStatus.CANCELLED
              AND EXISTS (SELECT 1 FROM TrainingWorkout ow
                          JOIN Horse oh ON ow.horseId = oh.id
                          WHERE ow.lotId = l.id
                            AND oh.ownerId = :ownerId
                            AND ow.status <> com.rtms.backend.training.enums.WorkoutStatus.CANCELLED)
            ORDER BY l.lotDate ASC, l.startTime ASC
            """)
    List<TrainingLotRow> findOwnerLotRows(@Param("ownerId") Long ownerId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query(LOT_ROW_SELECT + " WHERE l.id = :lotId")
    Optional<TrainingLotRow> findLotRowById(@Param("lotId") Long lotId);

    /**
     * Tên các ngựa còn hiệu lực trong NHIỀU lot bằng MỘT truy vấn (thay cho findAllById trong vòng lặp).
     * (Names of active horses in MANY lots with ONE query, replacing findAllById inside a loop.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.LotHorseNameRow(w.lotId, h.name)
           FROM TrainingWorkout w
           JOIN Horse h ON w.horseId = h.id
           WHERE w.lotId IN :lotIds
             AND w.status <> com.rtms.backend.training.enums.WorkoutStatus.CANCELLED
           ORDER BY w.lotId ASC, w.id ASC
           """)
    List<LotHorseNameRow> findActiveHorseNames(@Param("lotIds") Collection<Long> lotIds);

    /**
     * Như trên nhưng chỉ ngựa của một owner, để owner không thấy tên ngựa của chủ khác trong cùng lot.
     * (Same as above but only one owner horses, so an owner never sees the names of other owners horses.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.LotHorseNameRow(w.lotId, h.name)
           FROM TrainingWorkout w
           JOIN Horse h ON w.horseId = h.id
           WHERE w.lotId IN :lotIds
             AND h.ownerId = :ownerId
             AND w.status <> com.rtms.backend.training.enums.WorkoutStatus.CANCELLED
           ORDER BY w.lotId ASC, w.id ASC
           """)
    List<LotHorseNameRow> findActiveHorseNamesOfOwner(@Param("lotIds") Collection<Long> lotIds,
                                                      @Param("ownerId") Long ownerId);
}
