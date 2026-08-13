package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.AppUserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves a bearer API key into the account that owns it. */
@Component
@AllArgsConstructor
public class AuthProvider implements AuthenticationProvider {

  private final AppUserRepository appUserRepository;

  @Override
  public Authentication authenticate(Authentication authentication) {
    var apiKey = String.valueOf(authentication.getPrincipal());
    var user =
        appUserRepository
            .findByApiKey(apiKey)
            .orElseThrow(() -> new BadCredentialsException("Provided API key is not valid"));

    var principal = new Principal(user);
    return new PreAuthenticatedAuthenticationToken(
        principal, authentication.getCredentials(), principal.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
