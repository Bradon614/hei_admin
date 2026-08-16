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

  @Column(name = "old_value")
  private BigDecimal oldValue;

  @Column(name = "new_value", nullable = false)
  private BigDecimal newValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_type", nullable = false)
  private GradeChangeReasonType reasonType;

  @Column(name = "reason", nullable = false)
  private String reason;

  @ManyToOne(optional = false)
  @JoinColumn(name = "changed_by", nullable = false)
  @ToString.Exclude
  private AppUser changedBy;

  @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
  private Instant changedAt;
}
