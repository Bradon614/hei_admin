package com.exam.hei.endpoint.rest.security;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.exam.hei.endpoint.rest.model.Error;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AllArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Renders authorization failures as the {@code Error} payload of doc/api.yml.
 *
 * <p>Needed as soon as a per role rule exists: an authenticated caller without the required role
 * would otherwise get an empty 403 body, which does not match the specification.
 */
@AllArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    response.setStatus(SC_FORBIDDEN);
    response.setContentType(APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        Error.builder().type("ForbiddenException").message(exception.getMessage()).build());
  }
}
