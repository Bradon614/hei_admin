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

  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final AuthProvider subject = new AuthProvider(appUserRepository);

  private static AppUser user(Role role, String apiKey) {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .email("someone@hei.test")
        .passwordHash("hash")
        .role(role)
        .apiKey(apiKey)
        .build();
  }

  @Test
  void a_known_api_key_resolves_its_account() {
    var expected = user(Role.ADMIN, "known-key");
    when(appUserRepository.findByApiKey("known-key")).thenReturn(Optional.of(expected));

    var authentication =
        subject.authenticate(new PreAuthenticatedAuthenticationToken("known-key", "known-key"));

    assertTrue(authentication.isAuthenticated());
    assertEquals(expected, ((Principal) authentication.getPrincipal()).getUser());
  }

  @Test
  void the_resolved_account_carries_its_role_as_an_authority() {
    when(appUserRepository.findByApiKey("teacher-key"))
        .thenReturn(Optional.of(user(Role.TEACHER, "teacher-key")));

    var authentication =
        subject.authenticate(new PreAuthenticatedAuthenticationToken("teacher-key", "teacher-key"));

    assertEquals("ROLE_TEACHER", authentication.getAuthorities().iterator().next().getAuthority());
  }

  @Test
  void an_unknown_api_key_is_rejected() {
    when(appUserRepository.findByApiKey("nope")).thenReturn(Optional.empty());

    assertThrows(
        BadCredentialsException.class,
        () -> subject.authenticate(new PreAuthenticatedAuthenticationToken("nope", "nope")));
  }

  @Test
  void only_pre_authenticated_tokens_are_supported() {
    assertTrue(subject.supports(PreAuthenticatedAuthenticationToken.class));
    assertFalse(subject.supports(UsernamePasswordAuthenticationToken.class));
  }
}
