package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One of the six semesters of the curriculum, seeded by the V44 migration.
 *
 * <p>Read-only reference data: doc/api.yml exposes no endpoint to create or edit a semester, so
 * neither a service nor a controller exists for it.
 */
@Entity
@Table(name = "semester")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Semester {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "ref", nullable = false, unique = true)
  private SemesterRef ref;

  /** 1 to 6, the order the semesters are followed in. */
  @Column(name = "sem_order", nullable = false, unique = true)
  private int semOrder;

  /** 1 to 3. */
  @Column(name = "year_number", nullable = false)
  private int yearNumber;

  @Column(name = "required_credits", nullable = false)
  private int requiredCredits;

  /**
   * True for S1 to S3, where every student follows the same courses, false from S4 on, where a
   * track applies.
   *
   * <p>This flag is what keeps the common core rule out of the code: the semester a track must be
   * chosen at is read from here, never hardcoded.
   */
  @Column(name = "common_core", nullable = false)
  private boolean commonCore;
}
