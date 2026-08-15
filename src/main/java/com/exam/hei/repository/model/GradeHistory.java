package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One row per entry or update of a {@link Grade}. Never updated, never deleted.
 *
 * <p>Example: a grade going from 10 to 14 then to 13 leaves three rows behind (null -&gt; 10, 10
 * -&gt; 14, 14 -&gt; 13), each with its own reason and author, while {@code Grade.value} only ever
 * holds 13.
 *
 * <p>DB constraints: {@code reason} NOT NULL and non blank, {@code old_value IS DISTINCT FROM
 * new_value} — a change that does not change anything cannot be recorded.
 */
@Entity
@Table(name = "grade_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class GradeHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "grade_id", nullable = false)
  @ToString.Exclude
  private Grade grade;

  /** Null on the first entry. */
  @Column(name = "old_value")
  private BigDecimal oldValue;

  @Column(name = "new_value", nullable = false)
  private BigDecimal newValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_type", nullable = false)
  private GradeChangeReasonType reasonType;

  @Column(name = "reason", nullable = false)
  private String reason;

  /** The account that made the modification. */
  @ManyToOne(optional = false)
  @JoinColumn(name = "changed_by", nullable = false)
  @ToString.Exclude
  private AppUser changedBy;

  @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
  private Instant changedAt;
}
