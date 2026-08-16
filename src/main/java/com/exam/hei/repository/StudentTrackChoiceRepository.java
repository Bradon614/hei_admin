package com.exam.hei.repository;

import com.exam.hei.repository.model.StudentTrackChoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentTrackChoiceRepository extends JpaRepository<StudentTrackChoice, UUID> {
  List<StudentTrackChoice> findAllByStudentIdOrderByFromSemesterSemOrderAsc(UUID studentId);

  Optional<StudentTrackChoice> findByStudentIdAndFromSemesterId(UUID studentId, UUID semesterId);

  @Query(
      "select c from StudentTrackChoice c"
          + " where c.student.id = :studentId"
          + " and c.fromSemester.semOrder <= :semOrder"
          + " order by c.fromSemester.semOrder desc"
          + " limit 1")
  Optional<StudentTrackChoice> findRulingChoice(
      @Param("studentId") UUID studentId, @Param("semOrder") int semOrder);

  Optional<StudentTrackChoice> findFirstByStudentIdOrderByFromSemesterSemOrderDesc(UUID studentId);
}
