package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * An authenticable account.
 *
 * <p>Named {@code AppUser} after its table rather than {@code User}, which would collide with
 * {@link org.springframework.security.core.userdetails.User}.
 *
 * <p>A student and a teacher are business profiles, not accounts: {@code student.user_id} and
 * {@code teacher.user_id} point here, which is why accounts have to exist before either of them.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AppUser {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  /** Unused for now: the API has no login endpoint, authentication goes through {@link #apiKey}. */
  @Column(name = "password_hash", nullable = false)
  @ToString.Exclude
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private Role role;

  /** Credential expected in {@code Authorization: Bearer <api_key>}. */
  @Column(name = "api_key", nullable = false, unique = true)
  @ToString.Exclude
  private String apiKey;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;
}
