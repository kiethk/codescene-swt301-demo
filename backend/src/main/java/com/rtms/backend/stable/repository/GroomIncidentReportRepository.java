package com.rtms.backend.stable.repository;

import com.rtms.backend.stable.entity.GroomIncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

@Repository
public interface GroomIncidentReportRepository extends JpaRepository<GroomIncidentReport, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT report FROM GroomIncidentReport report WHERE report.id = :id")
    java.util.Optional<GroomIncidentReport> findByIdForUpdate(@Param("id") Long id);

    List<GroomIncidentReport> findByGroomId(Long groomId);

    List<GroomIncidentReport> findByHorseId(Long horseId);

    List<GroomIncidentReport> findAllByOrderByReportedAtDesc();

    long countByHorseIdAndReportedAtAfter(Long horseId, java.time.LocalDateTime after);

    /**
     * Số báo cáo sự cố của NHIỀU ngựa từ một mốc thời gian, bằng một câu GROUP BY.
     * (Incident-report counts for MANY horses since a time, with one GROUP BY.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.IncidentCountRow(r.horseId, COUNT(r))
           FROM GroomIncidentReport r
           WHERE r.horseId IN :horseIds AND r.reportedAt > :after
           GROUP BY r.horseId
           """)
    List<com.rtms.backend.training.dto.IncidentCountRow> countIncidentsByHorseIds(
            @Param("horseIds") java.util.Collection<Long> horseIds,
            @Param("after") java.time.LocalDateTime after);
}
