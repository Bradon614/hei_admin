package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class SecurityIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired JwtService jwtService;
  @Autowired javax.crypto.SecretKey jwtSigningKey;

  /**
   * See {@code AuthIT}: the JDK's {@code HttpURLConnection}, behind {@code TestRestTemplate} here,
   * cannot process a 401 answer to a POST at all.
   */
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private String tokenOf(Role role) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(UUID.randomUUID() + "@hei.test")
                .passwordHash("hash")
                .role(role)
                .build());
    return jwtService.issue(user).token();
  }

  private HttpStatus statusOf(String path, String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
    }
    return (HttpStatus)
        restTemplate
            .exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getStatusCode();
  }

  // --- public endpoints -----------------------------------------------------

  @Test
  void ping_stays_reachable_without_a_token() {
    // The POJA delivery pipeline probes this endpoint with curl --fail.
    assertEquals(OK, statusOf("/ping", null));
  }

  @Test
  void health_endpoints_stay_reachable_without_a_token() {
    // Asserting "not 401" rather than 200 on purpose: a health endpoint may legitimately report a
    // failure, what matters here is that security does not stand in front of it.
    assertNotEquals(UNAUTHORIZED, statusOf("/health/db", null));
  }

  @Test
  void a_failing_endpoint_reports_its_failure_rather_than_a_401() {
    // /health/bucket really uploads to S3 and fails against the dummy test bucket, so it exercises
    // the error dispatch. Spring re-dispatches to /error when a handler throws, and securing that
    // dispatch would turn every 500 into a misleading 401 — including on public endpoints.
    assertNotEquals(UNAUTHORIZED, statusOf("/health/bucket", null));
  }

  @Test
  void logging_in_needs_no_token() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"nobody@hei.test\",\"password\":\"whatever\"}"))
            .build();
    var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    // Reached the handler and was rejected for the credentials, not for a missing token.
    assertEquals(401, response.statusCode());
    assertTrue(response.body().contains("UnauthorizedException"), "body was " + response.body());
  }

  // --- everything else is closed -------------------------------------------

  @Test
  void whoami_without_a_token_is_unauthorized() {
    assertEquals(UNAUTHORIZED, statusOf("/whoami", null));
  }

  @Test
  void whoami_with_a_malformed_token_is_unauthorized() {
    assertEquals(UNAUTHORIZED, statusOf("/whoami", "not-a-jwt"));
  }

  @Test
  void an_unknown_path_is_closed_by_default() {
    // Guards the "deny by default" rule: a new endpoint is protected before anyone thinks about it.
    assertEquals(UNAUTHORIZED, statusOf("/promotions", null));
  }

  @Test
  void whoami_with_a_valid_token_is_authorized() {
    assertEquals(OK, statusOf("/whoami", tokenOf(Role.ADMIN)));
  }

  @Test
  void every_role_can_authenticate() {
    assertEquals(OK, statusOf("/whoami", tokenOf(Role.STUDENT)));
    assertEquals(OK, statusOf("/whoami", tokenOf(Role.TEACHER)));
    assertEquals(OK, statusOf("/whoami", tokenOf(Role.ADMIN)));
  }

  // --- what a JWT brings over an API key: a signature to forge ---------------

  @Test
  void an_expired_token_is_unauthorized() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(UUID.randomUUID() + "@hei.test")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .build());
    var now = Instant.now();
    var expired =
        io.jsonwebtoken.Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", "ADMIN")
            .issuedAt(Date.from(now.minusSeconds(7200)))
            .expiration(Date.from(now.minusSeconds(1)))
            .signWith(jwtSigningKey)
            .compact();

    assertEquals(UNAUTHORIZED, statusOf("/whoami", expired));
  }

  @Test
  void a_tampered_token_is_rejected() {
    var token = tokenOf(Role.STUDENT);
    var parts = token.split("\\.");
    var tamperedPayload =
        new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
            .replace("\"STUDENT\"", "\"ADMIN\"");
    var tampered =
        parts[0]
            + "."
            + java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tamperedPayload.getBytes())
            + "."
            + parts[2];

    assertEquals(UNAUTHORIZED, statusOf("/whoami", tampered));
  }

  // --- error contract -------------------------------------------------------

  @Test
  void an_authentication_failure_is_rendered_as_the_spec_error_payload() {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer not-a-jwt");

    var response =
        restTemplate.exchange("/whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertEquals(UNAUTHORIZED, response.getStatusCode());
    assertTrue(response.getBody().contains("\"type\""), "body was " + response.getBody());
    assertTrue(response.getBody().contains("\"message\""), "body was " + response.getBody());
  }
}
