package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.Role;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Identity and role of the authenticated caller.
 *
 * <p>doc/api.yml uses snake_case property names, hence the naming strategy: the generated
 * ObjectMapper of POJA keeps Jackson defaults, so each REST model states its own convention.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Whoami {

  private UUID userId;
  private String email;
  private Role role;

  /**
   * Filled in only when the role is STUDENT.
   *
   * <p>Always null for now: the student profile is introduced by its own feature. Same for {@link
   * #teacherId}.
   */
  private UUID studentId;

  /** Filled in only when the role is TEACHER. */
  private UUID teacherId;
}
