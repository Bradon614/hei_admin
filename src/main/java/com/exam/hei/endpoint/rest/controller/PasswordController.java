package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.model.PasswordChange;
import com.exam.hei.service.AccountService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class PasswordController {
  private final AccountService accountService;

  @PostMapping("/me/password")
  public ResponseEntity<Void> changeOwnPassword(@RequestBody PasswordChange body) {
    accountService.changeOwnPassword(body.getCurrentPassword(), body.getNewPassword());
    return ResponseEntity.ok().build();
  }
}
