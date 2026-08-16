package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminBootstrapperTest {
  private static final String EMAIL = "admin@hei.school";
  private static final String PASSWORD = "correct-horse-battery";

  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  private AdminBootstrapper bootstrapper(String email, String password) {
    return new AdminBootstrapper(appUserRepository, encoder, email, password);
  }

  private void noAdminYet() {
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
    when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  private AppUser created() {
    var captor = ArgumentCaptor.forClass(AppUser.class);
    verify(appUserRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void an_empty_deployment_gets_its_first_administrator() {
    noAdminYet();

    bootstrapper(EMAIL, PASSWORD).run(null);

    assertEquals(EMAIL, created().getEmail());
    assertEquals(Role.ADMIN, created().getRole());
  }

  @Test
  void the_password_is_hashed_and_never_stored_as_written() {
    noAdminYet();

    bootstrapper(EMAIL, PASSWORD).run(null);

    var hash = created().getPasswordHash();
    assertNotEquals(PASSWORD, hash);
    assertFalse(hash.contains(PASSWORD), "the plain password must not appear in the hash");
    assertTrue(hash.startsWith("$2"), "expected a BCrypt hash but was " + hash);
  }

  @Test
  void the_stored_hash_actually_verifies_the_password() {
    noAdminYet();

    bootstrapper(EMAIL, PASSWORD).run(null);

    assertTrue(encoder.matches(PASSWORD, created().getPasswordHash()));
    assertFalse(encoder.matches("wrong", created().getPasswordHash()));
  }

  @Test
  void an_existing_administrator_is_never_replaced() {
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    bootstrapper(EMAIL, PASSWORD).run(null);

    verify(appUserRepository, never()).save(any());
  }

  @Test
  void running_twice_creates_exactly_one_administrator() {
    noAdminYet();
    var subject = bootstrapper(EMAIL, PASSWORD);

    subject.run(null);
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(true);
    subject.run(null);

    verify(appUserRepository, org.mockito.Mockito.times(1)).save(any());
  }

  @Test
  void a_missing_email_bootstraps_nothing() {
    bootstrapper("", PASSWORD).run(null);

    verify(appUserRepository, never()).save(any());
    verify(appUserRepository, never()).existsByRole(any());
  }

  @Test
  void a_missing_password_bootstraps_nothing() {
    bootstrapper(EMAIL, "").run(null);

    verify(appUserRepository, never()).save(any());
  }

  @Test
  void blank_variables_count_as_missing() {
    bootstrapper("   ", "   ").run(null);

    verify(appUserRepository, never()).save(any());
  }

  @Test
  void an_email_already_taken_is_reported_rather_than_crashing_the_startup() {
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(appUserRepository.findByEmail(EMAIL))
        .thenReturn(Optional.of(AppUser.builder().id(UUID.randomUUID()).email(EMAIL).build()));

    bootstrapper(EMAIL, PASSWORD).run(null);

    verify(appUserRepository, never()).save(any());
  }

  @Test
  void startup_never_fails_whatever_the_configuration() {
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(appUserRepository.findByEmail(any())).thenReturn(Optional.empty());
    when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> {
          bootstrapper("", "").run(null);
          bootstrapper(EMAIL, "").run(null);
          bootstrapper("", PASSWORD).run(null);
          bootstrapper(EMAIL, PASSWORD).run(null);
        });
  }
}
