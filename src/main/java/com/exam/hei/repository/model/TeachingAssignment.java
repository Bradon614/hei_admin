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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * TERNARY relation Course x Teacher x Group.
 *
 * <p>The three rules "a course is taught by several teachers", "a course is given to several
 * groups" and "a teacher teaches several groups" cannot be represented by three binary join tables:
 * one would know that two teachers give Java and that Java is given to K1 and K2, without knowing
 * WHO teaches WHICH group. A missing row for a (course, group) pair means the course is not given
 * to that group.
 *
 * <p>Neither the semester nor the track is an extra axis: both are already determined by {@code
 * course}, and adding them here would introduce a functional dependency inside the key.
 *
 * <p>The database enforces {@code UNIQUE(course_id, teacher_id, group_id)}.
 */
@Entity
@Table(name = "teaching_assignment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TeachingAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  @ToString.Exclude
  private Course course;

  @ManyToOne(optional = false)
  @JoinColumn(name = "teacher_id", nullable = false)
  @ToString.Exclude
  private Teacher teacher;

  @ManyToOne(optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  @ToString.Exclude
  private Group group;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;
}
