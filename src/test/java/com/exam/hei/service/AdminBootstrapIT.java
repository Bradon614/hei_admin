package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.IsolatedFacadeIT;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The deadlock this feature exists to break, end to end.
 *
 * <p>Accounts are only ever created by {@code PUT /students} and {@code PUT /teachers}, both of
 * which require an ADMIN. On an empty database that means nobody can sign in and nobody can create
 * anyone. Asserting that the bootstrapped administrator can actually obtain a token is what proves
 * a fresh deployment is usable — checking that a row exists would not.
 *
 * <p>On {@link IsolatedFacadeIT} rather than the usual facade: the bootstrapper stands down as soon
 * as any administrator exists, and the database every other test shares is full of them within
 * seconds. A fresh deployment is only observable on a fresh schema.
 */
@TestPropertySource(
    properties = {
      "ADMIN_EMAIL=bootstrapped.admin@hei.school",
      "ADMIN_PASSWORD=correct-horse-battery"
    })
class AdminBootstrapIT extends IsolatedFacadeIT {

  private static final String EMAIL = "bootstrapped.admin@hei.school";
  private static final String PASSWORD = "correct-horse-battery";

  @Autowired AppUserRepository appUserRepository;
  @Autowired TestRestTemplate restTemplate;
  @Autowired ObjectMapper objectMapper;

  /**
   * See {@code AuthIT}: HttpURLConnection cannot process a 401 answer to a POST carrying a body.
   */
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private HttpResponse<String> login(String email, String password) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void the_administrator_named_by_the_environment_exists_after_startup() {
    var admin = appUserRepository.findByEmail(EMAIL).orElseThrow();

    assertEquals(Role.ADMIN, admin.getRole());
  }

  @Test
  void the_bootstrapped_administrator_can_actually_sign_in() throws Exception {
    var response = login(EMAIL, PASSWORD);

    assertEquals(200, response.statusCode(), "body was " + response.body());
    var token = objectMapper.readTree(response.body()).get("access_token").asText();
    assertNotNull(token);
    assertTrue(token.split("\\.").length == 3, "expected a JWT but was " + token);
  }

  @Test
  void the_password_from_the_environment_is_the_one_that_works() {
    var admin = appUserRepository.findByEmail(EMAIL).orElseThrow();

    assertTrue(
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .matches(PASSWORD, admin.getPasswordHash()));
  }

  @Test
  void the_plain_password_is_never_persisted() {
    var admin = appUserRepository.findByEmail(EMAIL).orElseThrow();

    assertTrue(admin.getPasswordHash().startsWith("$2"), "expected a BCrypt hash");
    assertTrue(!admin.getPasswordHash().contains(PASSWORD));
  }

  @Test
  void a_wrong_password_is_still_refused_for_the_bootstrapped_account() throws Exception {
    assertEquals(401, login(EMAIL, "not-the-password").statusCode());
  }

  @Test
  void exactly_one_administrator_was_created() {
    // The runner fires once per context; a second one would mean the idempotency guard is broken.
    var admins =
        appUserRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).toList();

    assertEquals(1, admins.size(), "administrators found: " + admins.size());
    assertEquals(EMAIL, admins.get(0).getEmail());
  }
}
