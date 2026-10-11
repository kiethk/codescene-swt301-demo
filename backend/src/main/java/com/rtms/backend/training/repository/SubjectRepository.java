package com.rtms.backend.training.repository;

import com.rtms.backend.training.entity.Subject;
import com.rtms.backend.training.enums.SubjectCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByCategory(SubjectCategory category);
}
