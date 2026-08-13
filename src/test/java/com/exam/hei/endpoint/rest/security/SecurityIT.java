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

  private String persistedApiKey(Role role) {
    var apiKey = UUID.randomUUID().toString();
    appUserRepository.save(
        AppUser.builder()
            .email(UUID.randomUUID() + "@hei.test")
            .passwordHash("hash")
            .role(role)
            .apiKey(apiKey)
            .build());
    return apiKey;
  }

  private HttpStatus statusOf(String path, String apiKey) {
    var headers = new HttpHeaders();
    if (apiKey != null) {
      headers.set(AUTHORIZATION, "Bearer " + apiKey);
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

  // --- everything else is closed -------------------------------------------

  @Test
  void whoami_without_a_token_is_unauthorized() {
    assertEquals(UNAUTHORIZED, statusOf("/whoami", null));
  }

  @Test
  void whoami_with_an_unknown_token_is_unauthorized() {
    assertEquals(UNAUTHORIZED, statusOf("/whoami", UUID.randomUUID().toString()));
  }

  @Test
  void an_unknown_path_is_closed_by_default() {
    // Guards the "deny by default" rule: a new endpoint is protected before anyone thinks about it.
    assertEquals(UNAUTHORIZED, statusOf("/promotions", null));
  }

  @Test
  void whoami_with_a_valid_token_is_authorized() {
    assertEquals(OK, statusOf("/whoami", persistedApiKey(Role.ADMIN)));
  }

  @Test
  void every_role_can_authenticate() {
    assertEquals(OK, statusOf("/whoami", persistedApiKey(Role.STUDENT)));
    assertEquals(OK, statusOf("/whoami", persistedApiKey(Role.TEACHER)));
    assertEquals(OK, statusOf("/whoami", persistedApiKey(Role.ADMIN)));
  }

  // --- error contract -------------------------------------------------------

  @Test
  void an_authentication_failure_is_rendered_as_the_spec_error_payload() {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + UUID.randomUUID());

    var response =
        restTemplate.exchange("/whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertEquals(UNAUTHORIZED, response.getStatusCode());
    assertTrue(response.getBody().contains("\"type\""), "body was " + response.getBody());
    assertTrue(response.getBody().contains("\"message\""), "body was " + response.getBody());
  }
}
