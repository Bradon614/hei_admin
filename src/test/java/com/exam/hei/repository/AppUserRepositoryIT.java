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

/** Covers the credential column added by the V45 migration. */
class AppUserRepositoryIT extends FacadeIT {

  @Autowired AppUserRepository appUserRepository;

  private static AppUser.AppUserBuilder validUser() {
    return AppUser.builder()
        .email(UUID.randomUUID() + "@hei.test")
        .passwordHash("hash")
        .role(Role.STUDENT)
        .apiKey(UUID.randomUUID().toString());
  }

  @Test
  void an_account_is_findable_by_its_api_key() {
    var saved = appUserRepository.save(validUser().role(Role.ADMIN).build());

    var found = appUserRepository.findByApiKey(saved.getApiKey()).orElseThrow();

    assertEquals(saved.getId(), found.getId());
    assertEquals(Role.ADMIN, found.getRole());
  }

  @Test
  void an_account_is_findable_by_its_email() {
    var saved = appUserRepository.save(validUser().build());

    assertEquals(
        saved.getId(), appUserRepository.findByEmail(saved.getEmail()).orElseThrow().getId());
  }

  @Test
  void an_unknown_api_key_resolves_to_nothing() {
    assertTrue(appUserRepository.findByApiKey(UUID.randomUUID().toString()).isEmpty());
  }

  @Test
  void two_accounts_cannot_share_the_same_api_key() {
    // Guards app_user_api_key_uq: a shared key would let one caller act as another account.
    var apiKey = UUID.randomUUID().toString();
    appUserRepository.saveAndFlush(validUser().apiKey(apiKey).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> appUserRepository.saveAndFlush(validUser().apiKey(apiKey).build()));
  }

  @Test
  void an_account_cannot_be_created_without_an_api_key() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> appUserRepository.saveAndFlush(validUser().apiKey(null).build()));
  }

  @Test
  void two_accounts_cannot_share_the_same_email() {
    var email = UUID.randomUUID() + "@hei.test";
    appUserRepository.saveAndFlush(validUser().email(email).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> appUserRepository.saveAndFlush(validUser().email(email).build()));
  }
}
