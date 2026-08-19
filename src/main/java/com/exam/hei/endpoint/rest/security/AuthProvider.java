package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AuthProvider implements AuthenticationProvider {
  private final JwtService jwtService;
  private final AppUserRepository appUserRepository;

  @Override
  public Authentication authenticate(Authentication authentication) {
    var token = String.valueOf(authentication.getPrincipal());
    var signed = jwtService.parse(token);
    var user =
        appUserRepository
            .findById(signed.getUser().getId())
            .filter(AppUser::isEnabled)
            .orElseThrow(() -> new BadCredentialsException("Provided token is not valid"));
    var principal = new Principal(user);
    return new PreAuthenticatedAuthenticationToken(
        principal, authentication.getCredentials(), principal.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
