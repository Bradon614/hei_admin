package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.StudentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
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

  /**
   * WRITE ONLY by convention: accepted on creation and update, never set by {@code toRest}. {@link
   * JsonInclude.Include#NON_NULL} drops the field from a response entirely rather than serializing
   * it as {@code "password":null}, since the mapper leaves it unset. Only its BCrypt hash is
   * stored, so this field is the sole way to give a student a password; left out on update, the
   * current password is kept.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @ToString.Exclude
  private String password;

  private LocalDate birthDate;
  private LocalDate entranceDate;
  private StudentStatus status;

  /** On writes, only the id of the promotion is read. */
  private Promotion promotion;

  /**
   * Track the student ends the curriculum in, computed and never stored.
   *
   * <p>Null while they are still in the common core. Never to be used to work out which courses
   * apply to them: that depends on the semester and is resolved through their track choices.
   */
  private Track currentTrack;

  /** Group the student belongs to right now, computed from the assignment left open. */
  private Group currentGroup;
}
