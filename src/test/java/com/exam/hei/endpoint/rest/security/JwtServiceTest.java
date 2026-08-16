package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class JwtServiceTest {
  private static SecretKey key(String seed) {
    return io.jsonwebtoken.security.Keys.hmacShaKeyFor((seed + "-".repeat(32)).getBytes());
  }

  private static final SecretKey KEY = key("jwt-service-test-signing-key");

  private final JwtService subject = new JwtService(KEY);

  private static AppUser user() {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .email("jean@hei.test")
        .role(Role.STUDENT)
        .build();
  }

  @Test
  void an_issued_token_resolves_back_to_its_account() {
    var user = user();

    var issued = subject.issue(user);
    var principal = subject.parse(issued.token());

    assertEquals(user.getId(), principal.getUser().getId());
    assertEquals(user.getEmail(), principal.getUser().getEmail());
    assertEquals(Role.STUDENT, principal.getUser().getRole());
  }

  @Test
  void the_role_travels_as_an_authority() {
    var issued = subject.issue(user());

    var authorities = subject.parse(issued.token()).getAuthorities();

    assertEquals("ROLE_STUDENT", authorities.iterator().next().getAuthority());
  }

  @Test
  void the_declared_lifetime_matches_the_configured_ttl() {
    assertEquals(JwtConf.TTL_SECONDS, subject.issue(user()).expiresInSeconds());
  }

  @Test
  void an_expired_token_is_rejected() {
    var now = Instant.now();
    var expired =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("email", "jean@hei.test")
            .claim("role", "STUDENT")
            .issuedAt(Date.from(now.minusSeconds(3600)))
            .expiration(Date.from(now.minusSeconds(1)))
            .signWith(KEY)
            .compact();

    assertThrows(BadCredentialsException.class, () -> subject.parse(expired));
  }

  @Test
  void a_tampered_token_is_rejected() {
    var issued = subject.issue(user());
    var parts = issued.token().split("\\.");
    var tamperedPayload =
        new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
            .replace("\"STUDENT\"", "\"ADMIN\"");
    var tampered =
        parts[0]
            + "."
            + java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tamperedPayload.getBytes())
            + "."
            + parts[2];

    assertThrows(BadCredentialsException.class, () -> subject.parse(tampered));
  }

  @Test
  void a_malformed_token_is_rejected() {
    assertThrows(BadCredentialsException.class, () -> subject.parse("not-a-jwt"));
  }

  @Test
  void a_token_signed_with_another_key_is_rejected() {
    var foreign = new JwtService(key("a-different-signing-key")).issue(user()).token();

    assertThrows(BadCredentialsException.class, () -> subject.parse(foreign));
  }
}
