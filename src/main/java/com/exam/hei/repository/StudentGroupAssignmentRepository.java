package com.exam.hei.repository;

import com.exam.hei.repository.model.StudentGroupAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentGroupAssignmentRepository
    extends JpaRepository<StudentGroupAssignment, UUID> {

  /** Every group the student went through, oldest first. */
  List<StudentGroupAssignment> findAllByStudentIdOrderByStartDateAsc(UUID studentId);

  /** The assignment currently open, if any. */
  Optional<StudentGroupAssignment> findByStudentIdAndEndDateIsNull(UUID studentId);

  /**
   * Answers "which group was the student in on a given date?".
   *
   * <p>Bounds are inclusive on both sides, matching the exclusion constraint of the schema: an
   * assignment ending on the 14th and one starting on the 15th do not overlap.
   */
  @Query(
      "select a from StudentGroupAssignment a"
          + " where a.student.id = :studentId"
          + " and a.startDate <= :date"
          + " and (a.endDate is null or a.endDate >= :date)")
  Optional<StudentGroupAssignment> findActiveAt(
      @Param("studentId") UUID studentId, @Param("date") LocalDate date);

  /**
   * Assignments of the student still running on or after the given date, used to reject a
   * conflicting change before the database constraint has to.
   *
   * <p>Takes no end date: a group change always opens an unbounded period, so anything not already
   * closed before that date overlaps it. Passing a nullable end date instead would bind an untyped
   * null, which PostgreSQL cannot cast.
   */
  @Query(
      "select a from StudentGroupAssignment a"
          + " where a.student.id = :studentId"
          + " and (a.endDate is null or a.endDate >= :startDate)")
  List<StudentGroupAssignment> findRunningOnOrAfter(
      @Param("studentId") UUID studentId, @Param("startDate") LocalDate startDate);
}
