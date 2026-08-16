package com.exam.hei.repository;

import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.SemesterRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GradeRepository extends JpaRepository<Grade, UUID> {
  Optional<Grade> findByExamIdAndStudentId(UUID examId, UUID studentId);

  List<Grade> findAllByExamId(UUID examId);

  List<Grade> findAllByStudentId(UUID studentId);

  @Query(
      "select g from Grade g where g.student.id = :studentId and g.exam.course.semester.ref ="
          + " :semesterRef")
  List<Grade> findAllByStudentIdAndSemesterRef(
      @Param("studentId") UUID studentId, @Param("semesterRef") SemesterRef semesterRef);
}
