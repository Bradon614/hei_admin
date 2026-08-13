package com.exam.hei.endpoint.rest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns {@code Authorization: Bearer <api_key>} into an authenticated security context.
 *
 * <p>Deliberately not a {@code @Component}: it is wired by {@link SecurityConf} only, so that
 * Spring Boot does not also register it as a plain servlet filter and run it twice per request.
 *
 * <p>A request without the header goes through untouched, which is what keeps the public endpoints
 * reachable. A request carrying an invalid key is rejected right here rather than being treated as
 * anonymous, so a typo in a key never looks like a missing permission.
 */
@AllArgsConstructor
public class BearerAuthFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthenticationManager authenticationManager;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    var apiKey = header.substring(BEARER_PREFIX.length()).trim();
    try {
      var authentication =
          authenticationManager.authenticate(
              new PreAuthenticatedAuthenticationToken(apiKey, apiKey));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (AuthenticationException e) {
      SecurityContextHolder.clearContext();
      authenticationEntryPoint.commence(request, response, e);
      return;
    }

    filterChain.doFilter(request, response);
  }
}
