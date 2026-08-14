package com.exam.hei.conf;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Project-specific test environment variables, picked up by {@link FacadeIT} through reflection so
 * that the POJA-generated facade never needs editing.
 */
public class EnvConf {

  void configureProperties(DynamicPropertyRegistry registry) {
    // 32+ chars: the minimum io.jsonwebtoken.security.Keys.hmacShaKeyFor accepts for HS256.
    registry.add("JWT_SECRET", () -> "test-jwt-signing-secret-not-for-production-use");
  }
}
