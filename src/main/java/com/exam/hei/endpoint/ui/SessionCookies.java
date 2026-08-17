package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

final class SessionCookies {

  private SessionCookies() {}

  static ResponseCookie issued(String token, long maxAgeSeconds) {
    return base(token).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
  }

  /**
   * A browser only replaces a cookie when the name, path and domain all match, so signing out has
   * to repeat every attribute the sign-in used. Both are built here for that reason: a logout that
   * quietly leaves the session alive would be worse than no logout at all.
   */
  static ResponseCookie cleared() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private static ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(BearerAuthFilter.COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/");
  }
}
