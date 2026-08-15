package com.exam.hei.service;

import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator, once, from the environment.
 *
 * <p>Without it a fresh deployment is unusable: accounts only ever come into existence through
 * {@code PUT /students} and {@code PUT /teachers}, both of which require an ADMIN, and no migration
 * seeds one. An empty database therefore has nobody who can sign in and nobody who can create
 * anyone — a deadlock that no amount of correct configuration resolves.
 *
 * <p>A runner rather than a migration: a Flyway script would have to carry a BCrypt hash, which
 * means committing a credential to the repository. Here the password lives only in the deployment
 * environment and is hashed before it is ever written.
 *
 * <p>Nothing here ever fails startup. The variables are absent in tests and on a developer machine,
 * and an application that refused to boot without them would take the whole suite down with it.
 * Every branch below logs and returns.
 */
@Component
@Slf4j
public class AdminBootstrapper implements ApplicationRunner {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final String adminEmail;
  private final String adminPassword;

  public AdminBootstrapper(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      @Value("${ADMIN_EMAIL:}") String adminEmail,
      @Value("${ADMIN_PASSWORD:}") String adminPassword) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (adminEmail.isBlank() || adminPassword.isBlank()) {
      log.info("ADMIN_EMAIL or ADMIN_PASSWORD is unset, no administrator will be bootstrapped");
      return;
    }

    // The idempotency guard: on every restart after the first, this is the branch that runs.
    if (appUserRepository.existsByRole(Role.ADMIN)) {
      log.info("An administrator already exists, nothing to bootstrap");
      return;
    }

    // email is UNIQUE, so a student or teacher already holding it would turn this into a startup
    // crash. Reported instead, since the deployment is otherwise healthy.
    if (appUserRepository.findByEmail(adminEmail).isPresent()) {
      log.error("ADMIN_EMAIL is already taken by a non administrator account, cannot bootstrap");
      return;
    }

    var admin =
        appUserRepository.save(
            AppUser.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build());

    // The email identifies which account was created; the password never reaches the log.
    log.info("Bootstrapped the first administrator: {}", admin.getEmail());
  }
}
