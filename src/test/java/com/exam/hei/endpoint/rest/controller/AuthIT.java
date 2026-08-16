package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired ObjectMapper objectMapper;

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private AppUser account(String email, String rawPassword, Role role) {
    return appUserRepository.save(
        AppUser.builder()
            .email(email)
            .passwordHash(ENCODER.encode(rawPassword))
            .role(role)
            .build());
  }

  private HttpResponse<String> login(String email, String password) throws Exception {
    var body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void correct_credentials_yield_a_usable_token() throws Exception {
    var email = UUID.randomUUID() + "@hei.test";
    account(email, "s3cret!!", Role.ADMIN);

    var response = login(email, "s3cret!!");

    assertEquals(200, response.statusCode());
    var token = objectMapper.readTree(response.body());
    assertTrue(token.has("access_token"));
    assertEquals("Bearer", token.get("token_type").asText());
    assertTrue(token.get("expires_in").asLong() > 0);

    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + token.get("access_token").asText());
    var whoami =
        restTemplate.exchange("/whoami", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertEquals(OK, whoami.getStatusCode());
  }

  @Test
  void a_wrong_password_is_unauthorized() throws Exception {
    var email = UUID.randomUUID() + "@hei.test";
    account(email, "s3cret!!", Role.ADMIN);

    var response = login(email, "wrong-password");

    assertEquals(401, response.statusCode());
    assertTrue(response.body().contains("UnauthorizedException"), "body was " + response.body());
  }

  @Test
  void an_unknown_email_is_unauthorized() throws Exception {
    var response = login(UUID.randomUUID() + "@hei.test", "whatever");

    assertEquals(401, response.statusCode());
  }

  @Test
  void login_needs_no_prior_token() throws Exception {
    var email = UUID.randomUUID() + "@hei.test";
    account(email, "s3cret!!", Role.STUDENT);

    assertEquals(200, login(email, "s3cret!!").statusCode());
  }
}
