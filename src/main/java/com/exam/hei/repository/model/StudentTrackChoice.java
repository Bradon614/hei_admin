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
 * The choice of an EL or TN track by a student, effective from a semester onwards.
 *
 * <p>A student follows the common core in S1, S2 and S3, then chooses a track from S4. A track is
 * therefore not a permanent property of a student but an academic context valid over a range of
 * semesters, which is why no track column exists on {@link Student}.
 *
 * <p>Deferred effect model: one row covers the nominal case, EL from S4 covering S4, S5 and S6. A
 * later reorientation is a further row with a higher semester, leaving the existing one untouched,
 * so the semesters it already covered keep their original track and the grades earned stay valid.
 */
@Entity
@Table(name = "student_track_choice")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class StudentTrackChoice {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  @ToString.Exclude
  private Student student;

  @ManyToOne(optional = false)
  @JoinColumn(name = "track_id", nullable = false)
  @ToString.Exclude
  private Track track;

  /** First semester this track applies to. */
  @ManyToOne(optional = false)
  @JoinColumn(name = "from_semester_id", nullable = false)
  @ToString.Exclude
  private Semester fromSemester;

  /**
   * Set by the application rather than left to the database default: the value is part of the
   * payload, and a default filled in by PostgreSQL would only be visible after re-reading the row.
   */
  @Column(name = "decided_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant decidedAt = Instant.now();

  @Column(name = "reason")
  private String reason;
}
