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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A student, and the permanent owner of their grades.
 *
 * <p>Three fields are deliberately absent:
 *
 * <ul>
 *   <li>no {@code group}: group membership changes over time and is historised separately, so a
 *       single column would lose the history;
 *   <li>no {@code track}: a student follows the common core until S3 and therefore has no track for
 *       the whole curriculum, the track being carried by their track choice;
 *   <li>no {@code graduated} flag: graduation is recomputed from S1 to S6, never stored.
 * </ul>
 */
@Entity
@Table(name = "student")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Student {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Student number, the "STD" column of the graduate export. */
  @Column(name = "ref", nullable = false, unique = true)
  private String ref;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "entrance_date", nullable = false)
  private LocalDate entranceDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private StudentStatus status = StudentStatus.ACTIVE;

  @ManyToOne(optional = false)
  @JoinColumn(name = "promotion_id", nullable = false)
  @ToString.Exclude
  private Promotion promotion;

  /** The account this student signs in with. One account backs at most one profile. */
  @OneToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  @ToString.Exclude
  private AppUser user;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  /**
   * Plaintext password carried from the REST payload to {@code StudentService}, which hashes it
   * into the account and discards it. Never persisted, never read back: {@code Student} the entity
   * has no password column, only {@code AppUser} does.
   */
  @Transient @ToString.Exclude private String password;
}
