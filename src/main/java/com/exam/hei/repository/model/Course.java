package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A subject of the curriculum.
 *
 * <p>A course belongs to exactly one semester, which is what makes the semester of a grade
 * derivable through grade to exam to course to semester, and why no grade stores a semester of its
 * own.
 */
@Entity
@Table(name = "course")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "ref", nullable = false, unique = true)
  private String ref;

  @Column(name = "title", nullable = false)
  private String title;

  /** Credits earned when the course is validated. */
  @Column(name = "credits", nullable = false)
  private int credits;

  @ManyToOne(optional = false)
  @JoinColumn(name = "semester_id", nullable = false)
  @ToString.Exclude
  private Semester semester;

  /**
   * Carries the critical EL / TN rule, as data rather than as a condition written in the code.
   *
   * <p>Crossed with {@code semester.commonCore}, it gives the four categories of course:
   *
   * <ul>
   *   <li>null on a common core semester: followed by every student;
   *   <li>null on a track semester: common to both tracks, and counted in the credits of both;
   *   <li>EL: invisible to a TN student;
   *   <li>TN: invisible to an EL student.
   * </ul>
   *
   * <p>A course of a common core semester must leave this null: no track applies before a track has
   * been chosen. The service enforces it, the rule spanning two tables.
   */
  @ManyToOne
  @JoinColumn(name = "track_id")
  @ToString.Exclude
  private Track track;
}
