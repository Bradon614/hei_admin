package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordChangeIT extends FacadeIT {

  private static final String CURRENT = "current-secret-2025";
  private static final String NEXT = "brand-new-secret-2026";

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser accountOf(Role role) {
    return appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash(passwordEncoder.encode(CURRENT))
            .role(role)
            .build());
  }

  private HttpResponse<String> changePassword(String token, String current, String next)
      throws Exception {
    var body =
        objectMapper.writeValueAsString(Map.of("current_password", current, "new_password", next));
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/me/password"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private int loginStatus(String email, String password) throws Exception {
    var body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  private int whoamiStatus(String token) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/whoami"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  @Test
  void a_signed_in_user_changes_their_own_password() throws Exception {
    var account = accountOf(Role.STUDENT);
    var token = jwtService.issue(account).token();

    var response = changePassword(token, CURRENT, NEXT);

    assertEquals(200, response.statusCode(), "body was " + response.body());
    assertEquals(200, loginStatus(account.getEmail(), NEXT));
  }

  @Test
  void the_former_password_stops_working() throws Exception {
    var account = accountOf(Role.STUDENT);
    changePassword(jwtService.issue(account).token(), CURRENT, NEXT);

    assertEquals(401, loginStatus(account.getEmail(), CURRENT));
  }

  @Test
  void every_token_issued_before_the_change_is_refused() throws Exception {
    var account = accountOf(Role.STUDENT);
    var issuedEarlier = jwtService.issue(account).token();
    assertEquals(200, whoamiStatus(issuedEarlier));

    changePassword(issuedEarlier, CURRENT, NEXT);

    assertEquals(401, whoamiStatus(issuedEarlier));
  }

  @Test
  void a_token_issued_after_the_change_works() throws Exception {
    var account = accountOf(Role.STUDENT);
    changePassword(jwtService.issue(account).token(), CURRENT, NEXT);
    var changed = appUserRepository.findById(account.getId()).orElseThrow();
    while (Instant.now().isBefore(changed.getPasswordChangedAt())) {
      Thread.sleep(50);
    }

    var reissued = jwtService.issue(changed);

    assertEquals(200, whoamiStatus(reissued.token()));
  }

  @Test
  void a_wrong_current_password_is_unauthorized_and_changes_nothing() throws Exception {
    var account = accountOf(Role.STUDENT);

    var response = changePassword(jwtService.issue(account).token(), "not-the-one", NEXT);

    assertEquals(401, response.statusCode(), "body was " + response.body());
    assertEquals(200, loginStatus(account.getEmail(), CURRENT));
  }

  @Test
  void a_new_password_under_eight_characters_is_a_bad_request() throws Exception {
    var account = accountOf(Role.STUDENT);

    var response = changePassword(jwtService.issue(account).token(), CURRENT, "short");

    assertEquals(400, response.statusCode(), "body was " + response.body());
    assertEquals(200, loginStatus(account.getEmail(), CURRENT));
  }

  @Test
  void resubmitting_the_current_password_is_a_bad_request() throws Exception {
    var account = accountOf(Role.STUDENT);

    var response = changePassword(jwtService.issue(account).token(), CURRENT, CURRENT);

    assertEquals(400, response.statusCode(), "body was " + response.body());
  }

  @Test
  void every_role_changes_its_own_password() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER, Role.ADMIN)) {
      var account = accountOf(role);

      var response = changePassword(jwtService.issue(account).token(), CURRENT, NEXT);

      assertEquals(200, response.statusCode(), "role " + role + ", body was " + response.body());
    }
  }

  @Test
  void an_anonymous_caller_changes_nothing() throws Exception {
    var response = changePassword(null, CURRENT, NEXT);

    assertEquals(401, response.statusCode(), "body was " + response.body());
  }

  @Test
  void the_endpoint_takes_no_identifier_so_no_other_account_can_be_named() throws Exception {
    var mine = accountOf(Role.STUDENT);
    var other = accountOf(Role.STUDENT);

    changePassword(jwtService.issue(mine).token(), CURRENT, NEXT);

    assertTrue(
        passwordEncoder.matches(
            CURRENT, appUserRepository.findById(other.getId()).orElseThrow().getPasswordHash()),
        "the other account must be untouched");
  }

  private HttpResponse<String> resetThrough(String path, Object payload, String token)
      throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + path))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void resetting_the_password_of_another_account_is_closed_to_a_student() throws Exception {
    var payload =
        List.of(
            Map.of(
                "ref",
                rand(20),
                "first_name",
                "Jean",
                "last_name",
                "Rakoto",
                "email",
                rand(12) + "@hei.test",
                "password",
                NEXT));

    var response =
        resetThrough("/students", payload, jwtService.issue(accountOf(Role.STUDENT)).token());

    assertEquals(403, response.statusCode(), "body was " + response.body());
  }

  @Test
  void resetting_the_password_of_another_account_is_closed_to_a_teacher() throws Exception {
    var payload =
        List.of(
            Map.of(
                "ref",
                rand(20),
                "first_name",
                "Aina",
                "last_name",
                "Randria",
                "email",
                rand(12) + "@hei.test",
                "password",
                NEXT));

    var response =
        resetThrough("/teachers", payload, jwtService.issue(accountOf(Role.TEACHER)).token());

    assertEquals(403, response.statusCode(), "body was " + response.body());
  }
}
