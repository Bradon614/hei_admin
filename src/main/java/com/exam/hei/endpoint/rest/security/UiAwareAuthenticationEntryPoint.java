package com.exam.hei.endpoint.rest.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AllArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

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
