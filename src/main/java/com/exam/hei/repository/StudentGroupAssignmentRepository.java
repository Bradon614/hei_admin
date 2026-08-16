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
  List<StudentGroupAssignment> findAllByStudentIdOrderByStartDateAsc(UUID studentId);

  Optional<StudentGroupAssignment> findByStudentIdAndEndDateIsNull(UUID studentId);

  @Query(
      "select a from StudentGroupAssignment a"
          + " where a.student.id = :studentId"
          + " and a.startDate <= :date"
          + " and (a.endDate is null or a.endDate >= :date)")
  Optional<StudentGroupAssignment> findActiveAt(
      @Param("studentId") UUID studentId, @Param("date") LocalDate date);

  @Query(
      "select a from StudentGroupAssignment a"
          + " where a.student.id = :studentId"
          + " and (a.endDate is null or a.endDate >= :startDate)")
  List<StudentGroupAssignment> findRunningOnOrAfter(
      @Param("studentId") UUID studentId, @Param("startDate") LocalDate startDate);
}
