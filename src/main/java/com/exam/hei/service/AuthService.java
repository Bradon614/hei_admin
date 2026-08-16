package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.model.IssuedToken;
import com.exam.hei.repository.AppUserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {
  private static final String INVALID_CREDENTIALS = "Invalid email or password";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public IssuedToken login(String email, String password) {
    var user = appUserRepository.findByEmail(email).orElseThrow(this::invalidCredentials);
    if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw invalidCredentials();
    }
    return jwtService.issue(user);
  }

  private BadCredentialsException invalidCredentials() {
    return new BadCredentialsException(INVALID_CREDENTIALS);
  }
}
