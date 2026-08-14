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
 * A teaching group of a promotion: K1, K2, K3.
 *
 * <p>Mapped to {@code student_group}, {@code group} being a reserved SQL word.
 *
 * <p>A group is a historical context, never the permanent identity of a student, which is why no
 * student points at one directly: membership lives in {@link StudentGroupAssignment}.
 */
@Entity
@Table(name = "student_group")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Group {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "ref", nullable = false)
  private String ref;

  @ManyToOne(optional = false)
  @JoinColumn(name = "promotion_id", nullable = false)
  @ToString.Exclude
  private Promotion promotion;

  /**
   * Null for a common core group, otherwise the track the group belongs to.
   *
   * <p>Only used to check the consistency of assignments. It is never read to determine which
   * courses apply to a student nor to compute results: those come from the track choice alone, so
   * there is no second source of truth for the EL / TN rule.
   */
  @ManyToOne
  @JoinColumn(name = "track_id")
  @ToString.Exclude
  private Track track;
}
