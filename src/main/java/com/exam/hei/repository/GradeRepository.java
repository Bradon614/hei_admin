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

  /** Identity of a grade: at most one per (student, exam), the source of the upsert decision. */
  Optional<Grade> findByExamIdAndStudentId(UUID examId, UUID studentId);

  List<Grade> findAllByExamId(UUID examId);

  List<Grade> findAllByStudentId(UUID studentId);

  /** The semester of a grade is derived through exam -&gt; course, never stored on the grade. */
  @Query(
      "select g from Grade g where g.student.id = :studentId and g.exam.course.semester.ref ="
          + " :semesterRef")
  List<Grade> findAllByStudentIdAndSemesterRef(
      @Param("studentId") UUID studentId, @Param("semesterRef") SemesterRef semesterRef);
}
