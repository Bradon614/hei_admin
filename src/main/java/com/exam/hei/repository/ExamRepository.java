package com.exam.hei.repository;

import com.exam.hei.repository.model.Exam;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamRepository extends JpaRepository<Exam, UUID> {
  List<Exam> findAllByCourseIdOrderByDateExamAsc(UUID courseId);

  Optional<Exam> findByCourseIdAndTitle(UUID courseId, String title);
}
