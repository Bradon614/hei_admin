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

  @ManyToOne(optional = false)
  @JoinColumn(name = "from_semester_id", nullable = false)
  @ToString.Exclude
  private Semester fromSemester;

  @Column(name = "decided_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant decidedAt = Instant.now();

  @Column(name = "reason")
  private String reason;
}
