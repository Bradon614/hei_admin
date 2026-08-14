package com.exam.hei.endpoint.rest.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Signing material for {@link JwtService}.
 *
 * <p>{@code JWT_SECRET} is set once in the POJA console, never in this repository: a secret
 * committed to source control stops being one. {@link Keys#hmacShaKeyFor} rejects anything under
 * 256 bits on its own, so a weak value fails application startup instead of shipping.
 */
@Configuration
public class JwtConf {

  /**
   * A token is validated by its signature alone, so nothing on the server can revoke one before it
   * expires. Kept short for that reason: 12 hours covers a working session without leaving a stolen
   * token usable for long.
   */
  static final long TTL_SECONDS = 12 * 3600;

  @Bean
  public SecretKey jwtSigningKey(@Value("${JWT_SECRET}") String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}
