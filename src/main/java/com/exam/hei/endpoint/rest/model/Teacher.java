package com.exam.hei.endpoint.rest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** A teacher, as exposed by the API. The API key of the underlying account is never exposed. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Teacher {

  private UUID id;
  private String ref;
  private String firstName;
  private String lastName;
  private String email;

  /**
   * WRITE ONLY by convention: accepted on creation and update, never set by {@code toRest}. {@link
   * JsonInclude.Include#NON_NULL} drops the field from a response entirely rather than serializing
   * it as {@code "password":null}, since the mapper leaves it unset. Only its BCrypt hash is
   * stored, so this field is the sole way to give a teacher a password; left out on update, the
   * current password is kept.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @ToString.Exclude
  private String password;
}
