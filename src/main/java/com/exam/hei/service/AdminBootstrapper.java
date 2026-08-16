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

    if (appUserRepository.existsByRole(Role.ADMIN)) {
      log.info("An administrator already exists, nothing to bootstrap");
      return;
    }

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

    log.info("Bootstrapped the first administrator: {}", admin.getEmail());
  }
}
