package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Current grade of a student for an exam.
 *
 * <p>Attached to the student and to the exam, NEVER to a group: this is the structural guarantee
 * that a group change cannot make a grade disappear. It carries no semester either, that being
 * derivable through the exam.
 *
 * <p>Every change also writes a {@link GradeHistory} row. {@code value} here is only ever the
 * current state; the trail of how it got there lives entirely in that other table.
 *
 * <p>DB constraints: {@code UNIQUE(student_id, exam_id)}, {@code 0 <= value <= 20}.
 */
@Entity
@Table(name = "grade")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Grade {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  @ToString.Exclude
  private Student student;

  @ManyToOne(optional = false)
  @JoinColumn(name = "exam_id", nullable = false)
  @ToString.Exclude
  private Exam exam;

  @Column(name = "value", nullable = false)
  private BigDecimal value;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  /**
   * Unlike {@code createdAt}, the database has no trigger to keep this current: the service sets it
   * explicitly on every write, creation included.
   */
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
