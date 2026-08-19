package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.endpoint.rest.security.model.SignedToken;
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

  public Principal parse(String token) {
    return new Principal(parseSigned(token).user());
  }

  public SignedToken parseSigned(String token) {
    try {
      var claims =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      var user =
          AppUser.builder()
              .id(UUID.fromString(claims.getSubject()))
              .email(claims.get(EMAIL_CLAIM, String.class))
              .role(Role.valueOf(claims.get(ROLE_CLAIM, String.class)))
              .build();
      return new SignedToken(user, claims.getIssuedAt().toInstant());
    } catch (JwtException | IllegalArgumentException e) {
      throw new BadCredentialsException("Provided token is not valid", e);
    }
  }
}
