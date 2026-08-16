package com.exam.hei.conf;

import org.springframework.test.context.DynamicPropertyRegistry;

public class EnvConf {
  void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("JWT_SECRET", () -> "test-jwt-signing-secret-not-for-production-use");
  }
}
