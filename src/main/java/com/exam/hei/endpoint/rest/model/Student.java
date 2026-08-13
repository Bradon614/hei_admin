package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.StudentStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A student, as exposed by the API.
 *
 * <p>The API key of the underlying account is deliberately absent: handing credentials out is an
 * administration task, not a payload.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Student {

  private UUID id;

  /** Student number, the "STD" column of the graduate export. */
  private String ref;

  private String firstName;
  private String lastName;
  private String email;
  private LocalDate birthDate;
  private LocalDate entranceDate;
  private StudentStatus status;

  /** On writes, only the id of the promotion is read. */
  private Promotion promotion;

  /**
   * Track applying to the last semester, computed and never stored.
   *
   * <p>Always null for now: a track comes from a track choice, which is introduced by its own
   * feature.
   *
   * <p>The {@code current_group} field of doc/api.yml is not exposed yet either: it is computed
   * from the group assignment history, which the group feature brings.
   */
  private Track currentTrack;
}
