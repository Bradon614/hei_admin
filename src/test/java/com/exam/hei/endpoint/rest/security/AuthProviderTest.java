package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class AuthProviderTest {
  private final JwtService jwtService = mock(JwtService.class);
  private final AuthProvider subject = new AuthProvider(jwtService);

  private static Principal principal(Role role) {
    return new Principal(
        AppUser.builder().id(UUID.randomUUID()).email("someone@hei.test").role(role).build());
  }

  @Test
  void a_valid_token_resolves_to_the_account_it_was_issued_for() {
    var expected = principal(Role.ADMIN);
    when(jwtService.parse("valid-token")).thenReturn(expected);

    var authentication =
        subject.authenticate(new PreAuthenticatedAuthenticationToken("valid-token", "valid-token"));

    assertTrue(authentication.isAuthenticated());
    assertEquals(expected, authentication.getPrincipal());
  }

  @Test
  void the_resolved_account_carries_its_role_as_an_authority() {
    when(jwtService.parse("teacher-token")).thenReturn(principal(Role.TEACHER));

    var authentication =
        subject.authenticate(
            new PreAuthenticatedAuthenticationToken("teacher-token", "teacher-token"));

    assertEquals("ROLE_TEACHER", authentication.getAuthorities().iterator().next().getAuthority());
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
