package com.exam.hei.endpoint.rest.security;

import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AuthProvider implements AuthenticationProvider {
  private final JwtService jwtService;

  @Override
  public Authentication authenticate(Authentication authentication) {
    var token = String.valueOf(authentication.getPrincipal());
    var principal = jwtService.parse(token);
    return new PreAuthenticatedAuthenticationToken(
        principal, authentication.getCredentials(), principal.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
