package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.model.Account;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.service.AccountService;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class AccountController {
  private final AccountService accountService;

  @PostMapping("/students/{id}/deactivation")
  public Account deactivateStudentAccount(@PathVariable UUID id) {
    return toRest(accountService.setStudentAccountEnabled(id, false));
  }

  @PostMapping("/students/{id}/activation")
  public Account activateStudentAccount(@PathVariable UUID id) {
    return toRest(accountService.setStudentAccountEnabled(id, true));
  }

  @PostMapping("/teachers/{id}/deactivation")
  public Account deactivateTeacherAccount(@PathVariable UUID id) {
    return toRest(accountService.setTeacherAccountEnabled(id, false));
  }

  @PostMapping("/teachers/{id}/activation")
  public Account activateTeacherAccount(@PathVariable UUID id) {
    return toRest(accountService.setTeacherAccountEnabled(id, true));
  }

  private static Account toRest(AppUser user) {
    return Account.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .enabled(user.isEnabled())
        .build();
  }
}
