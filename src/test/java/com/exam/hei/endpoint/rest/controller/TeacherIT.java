package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Teacher;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class TeacherIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String adminKey() {
    var apiKey = UUID.randomUUID().toString();
    appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash("hash")
            .role(Role.ADMIN)
            .apiKey(apiKey)
            .build());
    return apiKey;
  }

  private static HttpHeaders bearer(String apiKey) {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + apiKey);
    return headers;
  }

  private static Teacher aTeacher() {
    return Teacher.builder()
        .ref(rand(20))
        .firstName("Aina")
        .lastName("Randria")
        .email(rand(12) + "@hei.test")
        .build();
  }

  private ResponseEntity<List<Teacher>> put(List<Teacher> body, String apiKey) {
    return restTemplate.exchange(
        "/teachers",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        new ParameterizedTypeReference<>() {});
  }

  private Teacher created(String adminKey) {
    return put(List.of(aTeacher()), adminKey).getBody().get(0);
  }

  @Test
  void a_teacher_is_created_then_readable_by_its_id() {
    var admin = adminKey();
    var teacher = created(admin);

    assertNotNull(teacher.getId());

    var found =
        restTemplate.exchange(
            "/teachers/" + teacher.getId(), GET, new HttpEntity<>(bearer(admin)), Teacher.class);

    assertEquals(OK, found.getStatusCode());
    assertEquals(teacher.getRef(), found.getBody().getRef());
    assertEquals("Aina", found.getBody().getFirstName());
  }

  @Test
  void a_teacher_is_updated_rather_than_duplicated() {
    var admin = adminKey();
    var teacher = created(admin);
    teacher.setLastName("Rabemananjara");

    var updated = put(List.of(teacher), admin).getBody().get(0);

    assertEquals(teacher.getId(), updated.getId());
    assertEquals("Rabemananjara", updated.getLastName());
  }

  @Test
  void an_unknown_teacher_id_is_not_found() {
    var response =
        restTemplate.exchange(
            "/teachers/" + UUID.randomUUID(),
            GET,
            new HttpEntity<>(bearer(adminKey())),
            String.class);

    assertEquals(NOT_FOUND, response.getStatusCode());
    assertTrue(response.getBody().contains("NotFoundException"), "body was " + response.getBody());
  }

  @Test
  void teachers_are_listed_and_paginated() {
    var admin = adminKey();
    created(admin);
    created(admin);

    var page =
        restTemplate.exchange(
            "/teachers?page=1&page_size=1",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Teacher>>() {});

    assertEquals(1, page.getBody().size());
  }

  @Test
  void teachers_are_serialized_in_snake_case() {
    var admin = adminKey();
    var teacher = created(admin);

    var raw =
        restTemplate
            .exchange(
                "/teachers/" + teacher.getId(), GET, new HttpEntity<>(bearer(admin)), String.class)
            .getBody();

    assertTrue(raw.contains("\"first_name\""), "body was " + raw);
    assertFalse(raw.contains("\"firstName\""), "body was " + raw);
  }

  @Test
  void only_an_admin_can_write_teachers() {
    var body = List.of(aTeacher());

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      var apiKey = UUID.randomUUID().toString();
      appUserRepository.save(
          AppUser.builder()
              .email(rand(12) + "@hei.test")
              .passwordHash("hash")
              .role(role)
              .apiKey(apiKey)
              .build());

      var response =
          restTemplate.exchange(
              "/teachers", PUT, new HttpEntity<>(body, bearer(apiKey)), String.class);

      assertEquals(FORBIDDEN, response.getStatusCode(), "role " + role);
    }
  }

  @Test
  void reading_teachers_requires_authentication() {
    var response =
        restTemplate.exchange("/teachers", GET, new HttpEntity<>(new HttpHeaders()), String.class);

    assertEquals(UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void a_teacher_payload_never_carries_the_credentials_of_its_account() {
    var admin = adminKey();
    var teacher = created(admin);

    var raw =
        restTemplate
            .exchange(
                "/teachers/" + teacher.getId(), GET, new HttpEntity<>(bearer(admin)), String.class)
            .getBody();

    assertFalse(raw.contains("api_key"), "body was " + raw);
    assertFalse(raw.contains("password"), "body was " + raw);
  }
}
