package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

final class SessionCookies {

  private SessionCookies() {}

  static ResponseCookie issued(String token, long maxAgeSeconds) {
    return base(token).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
  }

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
