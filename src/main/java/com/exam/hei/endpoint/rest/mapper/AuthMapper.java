package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Token;
import com.exam.hei.model.IssuedToken;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {
  public Token toRest(IssuedToken issued) {
    return Token.builder()
        .accessToken(issued.token())
        .tokenType("Bearer")
        .expiresIn(issued.expiresInSeconds())
        .build();
  }
}
