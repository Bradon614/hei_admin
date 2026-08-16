package com.exam.hei.repository;

import com.exam.hei.repository.model.GradeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradeHistoryRepository extends JpaRepository<GradeHistory, UUID> {
  List<GradeHistory> findAllByGradeIdOrderByChangedAtDesc(UUID gradeId);
}
