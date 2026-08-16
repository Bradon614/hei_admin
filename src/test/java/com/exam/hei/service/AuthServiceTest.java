package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.model.IssuedToken;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final JwtService jwtService = mock(JwtService.class);
  private final AuthService subject =
      new AuthService(appUserRepository, passwordEncoder, jwtService);

  private static AppUser user() {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .email("jean@hei.test")
        .passwordHash("hashed")
        .role(Role.STUDENT)
        .build();
  }

  @Test
  void valid_credentials_are_exchanged_for_a_token() {
    var user = user();
    var issued = new IssuedToken("signed-jwt", 43200);
    when(appUserRepository.findByEmail("jean@hei.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("s3cret!!", "hashed")).thenReturn(true);
    when(jwtService.issue(user)).thenReturn(issued);

    assertEquals(issued, subject.login("jean@hei.test", "s3cret!!"));
  }

  @Test
  void a_wrong_password_is_rejected() {
    when(appUserRepository.findByEmail("jean@hei.test")).thenReturn(Optional.of(user()));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    assertThrows(BadCredentialsException.class, () -> subject.login("jean@hei.test", "wrong"));
  }

  @Test
  void an_unknown_email_is_rejected() {
    when(appUserRepository.findByEmail("nobody@hei.test")).thenReturn(Optional.empty());

    assertThrows(BadCredentialsException.class, () -> subject.login("nobody@hei.test", "whatever"));
  }

  @Test
  void an_unknown_email_and_a_wrong_password_report_the_exact_same_message() {
    when(appUserRepository.findByEmail("nobody@hei.test")).thenReturn(Optional.empty());
    when(appUserRepository.findByEmail("jean@hei.test")).thenReturn(Optional.of(user()));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    var unknownEmail =
        assertThrows(
            BadCredentialsException.class, () -> subject.login("nobody@hei.test", "whatever"));
    var wrongPassword =
        assertThrows(BadCredentialsException.class, () -> subject.login("jean@hei.test", "wrong"));

    assertEquals(unknownEmail.getMessage(), wrongPassword.getMessage());
  }

  @Test
  void a_missing_password_is_rejected_without_touching_the_encoder() {
    when(appUserRepository.findByEmail("jean@hei.test")).thenReturn(Optional.of(user()));

    assertThrows(BadCredentialsException.class, () -> subject.login("jean@hei.test", null));
  }
}
