package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Covers the credential column an account signs in with, checked by {@code POST /auth/login}. */
class AppUserRepositoryIT extends FacadeIT {

  @Autowired AppUserRepository appUserRepository;

  private static AppUser.AppUserBuilder validUser() {
    return AppUser.builder()
        .email(UUID.randomUUID() + "@hei.test")
        .passwordHash("hash")
        .role(Role.STUDENT);
  }

  @Test
  void an_account_is_findable_by_its_email() {
    var saved = appUserRepository.save(validUser().role(Role.ADMIN).build());

    var found = appUserRepository.findByEmail(saved.getEmail()).orElseThrow();

    assertEquals(saved.getId(), found.getId());
    assertEquals(Role.ADMIN, found.getRole());
  }

  @Test
  void an_unknown_email_resolves_to_nothing() {
    assertTrue(appUserRepository.findByEmail(UUID.randomUUID() + "@hei.test").isEmpty());
  }

  @Test
  void two_accounts_cannot_share_the_same_email() {
    var email = UUID.randomUUID() + "@hei.test";
    appUserRepository.saveAndFlush(validUser().email(email).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> appUserRepository.saveAndFlush(validUser().email(email).build()));
  }

  @Test
  void an_account_cannot_be_created_without_a_password_hash() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> appUserRepository.saveAndFlush(validUser().passwordHash(null).build()));
  }
}
