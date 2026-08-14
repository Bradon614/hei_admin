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
   * Track the student ends the curriculum in, computed and never stored.
   *
   * <p>Null while they are still in the common core. Never to be used to work out which courses
   * apply to them: that depends on the semester and is resolved through their track choices.
   */
  private Track currentTrack;

  /** Group the student belongs to right now, computed from the assignment left open. */
  private Group currentGroup;
}
