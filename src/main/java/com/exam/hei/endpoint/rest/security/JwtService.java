package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.model.IssuedToken;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Issues and validates the JWT that {@code Authorization: Bearer <token>} carries.
 *
 * <p>The token is self contained: it carries the account id, its email and its role, signed so that
 * a caller cannot alter them. Validating a request therefore never reads the database, which is
 * what {@link AuthProvider} relies on.
 */
@Service
@AllArgsConstructor
public class JwtService {

  private static final String EMAIL_CLAIM = "email";
  private static final String ROLE_CLAIM = "role";

  private final SecretKey signingKey;

  public IssuedToken issue(AppUser user) {
    var now = Instant.now();
    var expiry = now.plusSeconds(JwtConf.TTL_SECONDS);
    var token =
        Jwts.builder()
            .subject(user.getId().toString())
            .claim(EMAIL_CLAIM, user.getEmail())
            .claim(ROLE_CLAIM, user.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact();
    return new IssuedToken(token, JwtConf.TTL_SECONDS);
  }

  /**
   * @throws BadCredentialsException the token is malformed, its signature does not match, or it has
   *     expired. One exception for all three: which one it is tells an attacker something about the
   *     token they are probing.
   */
  public Principal parse(String token) {
    try {
      var claims =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      var user =
          AppUser.builder()
              .id(UUID.fromString(claims.getSubject()))
              .email(claims.get(EMAIL_CLAIM, String.class))
              .role(Role.valueOf(claims.get(ROLE_CLAIM, String.class)))
              .build();
      return new Principal(user);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BadCredentialsException("Provided token is not valid", e);
    }
  }
}
