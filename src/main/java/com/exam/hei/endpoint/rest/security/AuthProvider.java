package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import java.time.Instant;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AuthProvider implements AuthenticationProvider {
  private static final String REFUSED = "Provided token is not valid";

  private final JwtService jwtService;
  private final AppUserRepository appUserRepository;

  @Override
  public Authentication authenticate(Authentication authentication) {
    var token = String.valueOf(authentication.getPrincipal());
    var signed = jwtService.parseSigned(token);
    var user =
        appUserRepository
            .findById(signed.user().getId())
            .filter(AppUser::isEnabled)
            .orElseThrow(() -> new BadCredentialsException(REFUSED));
    if (issuedBeforeTheCurrentPassword(signed.issuedAt(), user)) {
      throw new BadCredentialsException(REFUSED);
    }
    var principal = new Principal(user);
    return new PreAuthenticatedAuthenticationToken(
        principal, authentication.getCredentials(), principal.getAuthorities());
  }

  private static boolean issuedBeforeTheCurrentPassword(Instant issuedAt, AppUser user) {
    return issuedAt.isBefore(user.getPasswordChangedAt());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
