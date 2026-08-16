package com.exam.hei.endpoint.rest.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConf {
  static final long TTL_SECONDS = 12 * 3600;

  @Bean
  public SecretKey jwtSigningKey(@Value("${JWT_SECRET}") String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}
