package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class LogoutIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired JwtService jwtService;

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(NEVER).build();

  private String tokenFor(Role role) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(UUID.randomUUID() + "@hei.test")
                .passwordHash("hash")
                .role(role)
                .build());
    return jwtService.issue(user).token();
  }

  private HttpResponse<String> logout(String cookieToken) throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/ui/logout"))
            .POST(HttpRequest.BodyPublishers.noBody());
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String cookieToken) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(restTemplate.getRootUri() + path));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void signing_out_sends_the_visitor_back_to_the_form() throws Exception {
    var response = logout(tokenFor(Role.ADMIN));

    assertEquals(302, response.statusCode());
    assertTrue(
        response.headers().firstValue("Location").orElseThrow().endsWith("/ui/login"),
        "Location was " + response.headers().firstValue("Location"));
  }

  @Test
  void signing_out_expires_the_session_cookie() throws Exception {
    var setCookie = logout(tokenFor(Role.ADMIN)).headers().firstValue("Set-Cookie").orElseThrow();

    assertTrue(setCookie.startsWith("access_token="), "cookie was " + setCookie);
    assertTrue(setCookie.contains("Max-Age=0"), "cookie was " + setCookie);
  }

  @Test
  void the_expiring_cookie_repeats_the_attributes_the_sign_in_used() throws Exception {
    var setCookie = logout(tokenFor(Role.STUDENT)).headers().firstValue("Set-Cookie").orElseThrow();

    assertTrue(setCookie.contains("Path=/"), "cookie was " + setCookie);
    assertTrue(setCookie.contains("HttpOnly"), "cookie was " + setCookie);
    assertTrue(setCookie.contains("SameSite=Strict"), "cookie was " + setCookie);
    assertTrue(setCookie.contains("Secure"), "cookie was " + setCookie);
  }

  @Test
  void signing_out_is_not_reachable_by_a_plain_link() throws Exception {
    assertEquals(405, get("/ui/logout", tokenFor(Role.ADMIN)).statusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/ui/me", "/ui/admin", "/ui/graduates"})
  void a_signed_out_visitor_is_redirected_to_the_form(String path) throws Exception {
    var response = get(path, null);

    assertEquals(302, response.statusCode(), path);
    assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/ui/login"), path);
  }

  @Test
  void a_cleared_cookie_no_longer_opens_a_page() throws Exception {
    assertEquals(302, get("/ui/me", "").statusCode());
  }
}
