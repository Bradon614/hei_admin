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

  /** Choices of the student, earliest effective semester first. */
  List<StudentTrackChoice> findAllByStudentIdOrderByFromSemesterSemOrderAsc(UUID studentId);

  Optional<StudentTrackChoice> findByStudentIdAndFromSemesterId(UUID studentId, UUID semesterId);

  /**
   * The choice ruling a given semester: the latest one taking effect at or before it.
   *
   * <p>This is what makes a track depend on the semester rather than on the student, and it backs
   * the resolution used by transcripts, credits and diploma eligibility.
   */
  @Query(
      "select c from StudentTrackChoice c"
          + " where c.student.id = :studentId"
          + " and c.fromSemester.semOrder <= :semOrder"
          + " order by c.fromSemester.semOrder desc"
          + " limit 1")
  Optional<StudentTrackChoice> findRulingChoice(
      @Param("studentId") UUID studentId, @Param("semOrder") int semOrder);

  /**
   * The most recent choice, whatever the semester: the track the student ends the curriculum in.
   */
  Optional<StudentTrackChoice> findFirstByStudentIdOrderByFromSemesterSemOrderDesc(UUID studentId);
}
