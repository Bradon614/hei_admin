package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/** Where the token is read from: the header first, the UI cookie as a fallback. */
class BearerAuthFilterTest {

  private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
  private final AuthenticationEntryPoint entryPoint = mock(AuthenticationEntryPoint.class);
  private final BearerAuthFilter subject = new BearerAuthFilter(authenticationManager, entryPoint);

  private static final String HEADER_TOKEN = "token-from-header";
  private static final String COOKIE_TOKEN = "token-from-cookie";

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void resolves(String token) {
    var principal = new Principal(AppUser.builder().id(UUID.randomUUID()).role(Role.ADMIN).build());
    when(authenticationManager.authenticate(new PreAuthenticatedAuthenticationToken(token, token)))
        .thenReturn(
            new PreAuthenticatedAuthenticationToken(principal, token, principal.getAuthorities()));
  }

  private String authenticatedTokenAfter(MockHttpServletRequest request) throws Exception {
    subject.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : String.valueOf(authentication.getCredentials());
  }

  @Test
  void a_bearer_header_authenticates() throws Exception {
    resolves(HEADER_TOKEN);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

    assertEquals(HEADER_TOKEN, authenticatedTokenAfter(request));
  }

  @Test
  void the_ui_cookie_authenticates_when_no_header_is_present() throws Exception {
    // The whole reason the cookie exists: a browser loading a page sends no Authorization header.
    resolves(COOKIE_TOKEN);
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(BearerAuthFilter.COOKIE_NAME, COOKIE_TOKEN));

    assertEquals(COOKIE_TOKEN, authenticatedTokenAfter(request));
  }

  @Test
  void the_header_wins_over_the_cookie() throws Exception {
    // An API client's behaviour must not change because its cookie jar happens to hold a session.
    resolves(HEADER_TOKEN);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);
    request.setCookies(new Cookie(BearerAuthFilter.COOKIE_NAME, COOKIE_TOKEN));

    assertEquals(HEADER_TOKEN, authenticatedTokenAfter(request));
  }

  @Test
  void neither_header_nor_cookie_stays_anonymous() throws Exception {
    assertNull(authenticatedTokenAfter(new MockHttpServletRequest()));

    verify(authenticationManager, never()).authenticate(any());
  }

  @Test
  void an_unrelated_cookie_is_ignored() throws Exception {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("theme", "dark"));

    assertNull(authenticatedTokenAfter(request));
  }

  @Test
  void a_blank_cookie_is_ignored_rather_than_rejected() throws Exception {
    // A browser clearing the cookie leaves an empty value behind; that is a signed-out visitor, not
    // a bad credential.
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(BearerAuthFilter.COOKIE_NAME, ""));

    assertNull(authenticatedTokenAfter(request));

    verify(authenticationManager, never()).authenticate(any());
  }

  @Test
  void an_invalid_cookie_is_rejected_rather_than_treated_as_anonymous() throws Exception {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("expired"));
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(BearerAuthFilter.COOKIE_NAME, "expired-token"));
    var response = new MockHttpServletResponse();

    subject.doFilterInternal(request, response, new MockFilterChain());

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(entryPoint).commence(any(), any(), any());
  }
}
