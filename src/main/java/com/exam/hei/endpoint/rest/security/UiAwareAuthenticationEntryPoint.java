package com.exam.hei.endpoint.rest.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AllArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers an unauthenticated caller in the shape it can actually use: a redirect to the sign-in
 * page for the Thymeleaf routes, the JSON {@code Error} payload everywhere else.
 *
 * <p>Without this, a browser landing on a page while signed out would be shown a raw JSON 401
 * rather than the form that fixes it.
 */
@AllArgsConstructor
public class UiAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final String UI_PREFIX = "/ui/";
  private static final String LOGIN_PATH = "/ui/login";

  private final AuthenticationEntryPoint restEntryPoint;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    if (request.getRequestURI().startsWith(UI_PREFIX)) {
      response.sendRedirect(request.getContextPath() + LOGIN_PATH);
      return;
    }
    restEntryPoint.commence(request, response, exception);
  }
}
