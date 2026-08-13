package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Whoami;
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
import org.springframework.http.ResponseEntity;

class WhoamiIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;

  private AppUser persistedUser(Role role) {
    return appUserRepository.save(
        AppUser.builder()
            .email(UUID.randomUUID() + "@hei.test")
            .passwordHash("hash")
            .role(role)
            .apiKey(UUID.randomUUID().toString())
            .build());
  }

  private <T> ResponseEntity<T> whoamiAs(AppUser user, Class<T> responseType) {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + user.getApiKey());
    return restTemplate.exchange(
        "/whoami", HttpMethod.GET, new HttpEntity<>(headers), responseType);
  }

  @Test
  void whoami_returns_the_identity_of_the_calling_account() {
    var user = persistedUser(Role.ADMIN);

    var body = whoamiAs(user, Whoami.class).getBody();

    assertEquals(user.getId(), body.getUserId());
    assertEquals(user.getEmail(), body.getEmail());
    assertEquals(Role.ADMIN, body.getRole());
  }

  @Test
  void whoami_reports_the_role_of_each_account() {
    assertEquals(
        Role.STUDENT, whoamiAs(persistedUser(Role.STUDENT), Whoami.class).getBody().getRole());
    assertEquals(
        Role.TEACHER, whoamiAs(persistedUser(Role.TEACHER), Whoami.class).getBody().getRole());
  }

  @Test
  void whoami_does_not_link_a_profile_yet() {
    // student_id and teacher_id are part of the contract but stay null until the student and
    // teacher profiles exist.
    var body = whoamiAs(persistedUser(Role.STUDENT), Whoami.class).getBody();

    assertNull(body.getStudentId());
    assertNull(body.getTeacherId());
  }

  @Test
  void whoami_is_serialized_in_snake_case() {
    var raw = whoamiAs(persistedUser(Role.ADMIN), String.class).getBody();

    assertTrue(raw.contains("\"user_id\""), "body was " + raw);
    assertFalse(raw.contains("\"userId\""), "body was " + raw);
  }

  @Test
  void whoami_never_exposes_the_credential() {
    var raw = whoamiAs(persistedUser(Role.ADMIN), String.class).getBody();

    assertFalse(raw.contains("api_key"), "body was " + raw);
    assertFalse(raw.contains("password"), "body was " + raw);
  }
}
