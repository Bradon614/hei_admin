package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The membership of a student in a group, over a period.
 *
 * <p>A student may change group at any moment: during a year, during a semester, between two
 * semesters, and several times within the same year or the same semester. No academic year appears
 * here on purpose: only dates matter, so nothing limits how often a change may happen.
 *
 * <p>A null {@link #endDate} means the assignment is currently active.
 *
 * <p>The database forbids two overlapping periods for the same student through an exclusion
 * constraint. The service checks before writing so that a conflict surfaces as a clean 409, the
 * constraint staying as the last line of defence.
 */
@Entity
@Table(name = "student_group_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class StudentGroupAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  @ToString.Exclude
  private Student student;

  @ManyToOne(optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  @ToString.Exclude
  private Group group;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  /** Null while the assignment is the current one. */
  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "reason")
  private String reason;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;
}
