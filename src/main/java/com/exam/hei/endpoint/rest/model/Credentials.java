package com.exam.hei.endpoint.rest.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Payload of {@code POST /auth/login}.
 *
 * <p>Never serialized by this application: {@code AuthController} only ever reads it as a request
 * body. No write-only marker is needed for that reason, unlike {@link Student#getPassword()} and
 * {@link Teacher#getPassword()}, which the same process also serializes back as a response.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Credentials {

  private String email;

  @ToString.Exclude private String password;
}
