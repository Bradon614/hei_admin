package com.exam.hei.endpoint.rest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a JWT into an authenticated security context, read from {@code Authorization: Bearer
 * <token>} or, failing that, from the {@value #COOKIE_NAME} cookie.
 *
 * <p>Deliberately not a {@code @Component}: it is wired by {@link SecurityConf} only, so that
 * Spring Boot does not also register it as a plain servlet filter and run it twice per request.
 *
 * <p>The cookie exists for the Thymeleaf pages only: a browser loading a page sends no {@code
 * Authorization} header, so header-only authentication would make every page a 401. The header
 * keeps priority, so an API client's behaviour is unchanged whatever cookies its jar happens to
 * hold.
 *
 * <p>A request carrying neither goes through untouched, which is what keeps the public endpoints
 * reachable. A request carrying an invalid token is rejected right here rather than being treated
 * as anonymous, so a malformed or expired token never looks like a missing permission.
 */
@AllArgsConstructor
public class BearerAuthFilter extends OncePerRequestFilter {

  /** Set by the UI login, never by the REST API. See {@code AuthPageController}. */
  public static final String COOKIE_NAME = "access_token";

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthenticationManager authenticationManager;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var token = tokenOf(request);
    if (token.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      var authentication =
          authenticationManager.authenticate(
              new PreAuthenticatedAuthenticationToken(token.get(), token.get()));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (AuthenticationException e) {
      SecurityContextHolder.clearContext();
      authenticationEntryPoint.commence(request, response, e);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private Optional<String> tokenOf(HttpServletRequest request) {
    var header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }
    return cookieToken(request);
  }

  private Optional<String> cookieToken(HttpServletRequest request) {
    var cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }
}
