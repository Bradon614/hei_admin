package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class AuthProviderTest {
  private final JwtService jwtService = mock(JwtService.class);
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final AuthProvider subject = new AuthProvider(jwtService, appUserRepository);

  private static AppUser user(Role role, boolean enabled) {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .email("someone@hei.test")
        .role(role)
        .enabled(enabled)
        .build();
  }

  private AppUser signedIn(String token, Role role, boolean enabled) {
    var user = user(role, enabled);
    when(jwtService.parse(token)).thenReturn(new Principal(user));
    when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
    return user;
  }

  @Test
  void a_valid_token_resolves_to_the_account_it_was_issued_for() {
    var user = signedIn("valid-token", Role.ADMIN, true);

    var authentication =
        subject.authenticate(new PreAuthenticatedAuthenticationToken("valid-token", "valid-token"));

    assertTrue(authentication.isAuthenticated());
    assertEquals(new Principal(user), authentication.getPrincipal());
  }

  @Test
  void the_resolved_account_carries_its_role_as_an_authority() {
    signedIn("teacher-token", Role.TEACHER, true);

    var authentication =
        subject.authenticate(
            new PreAuthenticatedAuthenticationToken("teacher-token", "teacher-token"));

    assertEquals("ROLE_TEACHER", authentication.getAuthorities().iterator().next().getAuthority());
  }

  @Test
  void a_token_signed_for_a_disabled_account_is_rejected() {
    signedIn("disabled-token", Role.STUDENT, false);

    assertThrows(
        BadCredentialsException.class,
        () ->
            subject.authenticate(
                new PreAuthenticatedAuthenticationToken("disabled-token", "disabled-token")));
  }

  @Test
  void a_token_signed_for_an_account_that_no_longer_exists_is_rejected() {
    var user = user(Role.ADMIN, true);
    when(jwtService.parse("stale-token")).thenReturn(new Principal(user));
    when(appUserRepository.findById(user.getId())).thenReturn(Optional.empty());

    assertThrows(
        BadCredentialsException.class,
        () ->
            subject.authenticate(
                new PreAuthenticatedAuthenticationToken("stale-token", "stale-token")));
  }

  @Test
  void the_role_comes_from_the_database_rather_than_the_token() {
    var user = user(Role.STUDENT, true);
    when(jwtService.parse("stale-role"))
        .thenReturn(
            new Principal(
                AppUser.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .role(Role.ADMIN)
                    .build()));
    when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));

    var authentication =
        subject.authenticate(new PreAuthenticatedAuthenticationToken("stale-role", "stale-role"));

    assertEquals("ROLE_STUDENT", authentication.getAuthorities().iterator().next().getAuthority());
  }

  @Test
  void an_invalid_token_is_rejected() {
    when(jwtService.parse("garbage")).thenThrow(new BadCredentialsException("Provided token"));

    assertThrows(
        BadCredentialsException.class,
        () -> subject.authenticate(new PreAuthenticatedAuthenticationToken("garbage", "garbage")));
  }

  @Test
  void only_pre_authenticated_tokens_are_supported() {
    assertTrue(subject.supports(PreAuthenticatedAuthenticationToken.class));
    assertFalse(subject.supports(UsernamePasswordAuthenticationToken.class));
  }
}
