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
 * One assessment of a course. A course may hold several, each with its own weight.
 *
 * <p>The date is not decoration: it is what resolves the group a student belonged to when the exam
 * took place, which the teacher authorization rule relies on.
 */
@Entity
@Table(name = "exam")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Exam {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  @ToString.Exclude
  private Course course;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "date_exam", nullable = false)
  private Instant dateExam;

  /** Weight of this exam in the final grade of its course. */
  @Column(name = "coefficient", nullable = false)
  private BigDecimal coefficient;
}
