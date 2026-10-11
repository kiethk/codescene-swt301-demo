package com.rtms.backend.training.repository;

import com.rtms.backend.training.dto.CourseSubjectResponse;
import com.rtms.backend.training.entity.CourseSubject;
import com.rtms.backend.training.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseSubjectRepository extends JpaRepository<CourseSubject, Long> {

    long countByCourseId(Long courseId);

    /**
     * Các bài tập của khóa theo đúng thứ tự (một câu JOIN thay cho findById từng bài).
     * (A course subjects in order — one JOIN instead of one findById per subject.)
     */
    @Query("""
           SELECT s FROM CourseSubject cs
           JOIN Subject s ON cs.subjectId = s.id
           WHERE cs.courseId = :courseId
           ORDER BY cs.orderIndex ASC
           """)
    List<Subject> findSubjectsOfCourseInOrder(@Param("courseId") Long courseId);

    /**
     * Các bài tập của khóa dưới dạng response, đã JOIN sẵn thông tin bài tập.
     * (A course subjects as response DTOs with the subject details already joined.)
     */
    @Query("""
           SELECT new com.rtms.backend.training.dto.CourseSubjectResponse(
                      cs.id, s.id, s.name, s.description, s.surfaceType, s.targetDistanceMeters,
                      s.intensityLevel, cs.orderIndex, s.durationMinutes)
           FROM CourseSubject cs
           JOIN Subject s ON cs.subjectId = s.id
           WHERE cs.courseId = :courseId
           ORDER BY cs.orderIndex ASC
           """)
    List<CourseSubjectResponse> findCourseSubjectResponses(@Param("courseId") Long courseId);
}
