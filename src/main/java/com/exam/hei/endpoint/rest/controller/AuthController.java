package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.AuthMapper;
import com.exam.hei.endpoint.rest.model.Credentials;
import com.exam.hei.endpoint.rest.model.Token;
import com.exam.hei.service.AuthService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The only endpoint a caller reaches before holding a token: it is what hands one out. */
@RestController
@AllArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final AuthMapper authMapper;

  @PostMapping("/auth/login")
  public Token login(@RequestBody Credentials credentials) {
    return authMapper.toRest(authService.login(credentials.getEmail(), credentials.getPassword()));
  }
}
