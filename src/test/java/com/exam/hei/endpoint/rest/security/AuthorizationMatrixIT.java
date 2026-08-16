package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

class AuthorizationMatrixIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired JwtService jwtService;

  private static final UUID ANY = UUID.randomUUID();

  private String student;
  private String teacher;
  private String admin;

  @BeforeEach
  void issueTokens() {
    student = tokenFor(Role.STUDENT);
    teacher = tokenFor(Role.TEACHER);
    admin = tokenFor(Role.ADMIN);
  }

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

  private String tokenOf(String role) {
    return switch (role) {
      case "STUDENT" -> student;
      case "TEACHER" -> teacher;
      case "ADMIN" -> admin;
      default -> null;
    };
  }

  private int statusOf(String role, HttpMethod method, String path) {
    var headers = new HttpHeaders();
    var token = tokenOf(role);
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
    }

    var body = method == HttpMethod.PUT || method == HttpMethod.POST ? "[]" : null;
    if (body != null) {
      headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    }
    return restTemplate
        .exchange(path, method, new HttpEntity<>(body, headers), String.class)
        .getStatusCode()
        .value();
  }

  private void assertForbidden(String role, HttpMethod method, String path) {
    assertEquals(403, statusOf(role, method, path), role + " " + method + " " + path);
  }

  private void assertAllowedThrough(String role, HttpMethod method, String path) {
    var status = statusOf(role, method, path);
    assertEquals(
        false,
        status == 403 || status == 401,
        role + " " + method + " " + path + " was refused with " + status);
  }

  @ParameterizedTest
  @CsvSource({
    "PUT, /promotions",
    "PUT, /tracks",
    "PUT, /students",
    "PUT, /teachers",
    "PUT, /courses",
    "PUT, /teaching-assignments",
  })
  void reference_data_is_written_by_an_admin_alone(HttpMethod method, String path) {
    assertForbidden("STUDENT", method, path);
    assertForbidden("TEACHER", method, path);
    assertAllowedThrough("ADMIN", method, path);
  }

  @ParameterizedTest
  @CsvSource({"PUT, /promotions/{id}/groups"})
  void nested_reference_data_is_written_by_an_admin_alone(HttpMethod method, String path) {
    var resolved = path.replace("{id}", ANY.toString());

    assertForbidden("STUDENT", method, resolved);
    assertForbidden("TEACHER", method, resolved);
    assertAllowedThrough("ADMIN", method, resolved);
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /students/{id}/group-assignments",
    "POST, /students/{id}/track-choices",
  })
  void moving_a_student_is_an_administrative_act(HttpMethod method, String path) {
    var resolved = path.replace("{id}", ANY.toString());

    assertForbidden("STUDENT", method, resolved);
    assertForbidden("TEACHER", method, resolved);
    assertAllowedThrough("ADMIN", method, resolved);
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /promotions/{id}/results",
    "GET, /promotions/{id}/graduates",
    "GET, /promotions/{id}/graduates/excel",
  })
  void promotion_wide_listings_are_administrative(HttpMethod method, String path) {
    var resolved = path.replace("{id}", ANY.toString());

    assertForbidden("STUDENT", method, resolved);
    assertForbidden("TEACHER", method, resolved);
    assertAllowedThrough("ADMIN", method, resolved);
  }

  @ParameterizedTest
  @CsvSource({"GET, /students", "GET, /teachers"})
  void a_directory_is_never_handed_to_a_student(HttpMethod method, String path) {
    assertForbidden("STUDENT", method, path);
    assertAllowedThrough("TEACHER", method, path);
    assertAllowedThrough("ADMIN", method, path);
  }

  @ParameterizedTest
  @CsvSource({"PUT, /courses/{id}/exams", "PUT, /exams/{id}/grades"})
  void teaching_writes_are_closed_to_students(HttpMethod method, String path) {
    var resolved = path.replace("{id}", ANY.toString());

    assertForbidden("STUDENT", method, resolved);
    assertAllowedThrough("ADMIN", method, resolved);
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /courses",
    "GET, /tracks",
    "GET, /promotions",
    "GET, /whoami",
  })
  void reference_data_is_readable_by_anyone_signed_in(HttpMethod method, String path) {
    assertAllowedThrough("STUDENT", method, path);
    assertAllowedThrough("TEACHER", method, path);
    assertAllowedThrough("ADMIN", method, path);
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /students",
    "GET, /teachers",
    "GET, /courses",
    "GET, /tracks",
    "GET, /promotions",
    "GET, /whoami",
  })
  void an_anonymous_caller_is_refused_everywhere(HttpMethod method, String path) {
    assertEquals(401, statusOf("ANONYMOUS", method, path), "anonymous " + method + " " + path);
  }

  @ParameterizedTest
  @CsvSource({"GET, /ping", "GET, /health/db"})
  void the_probes_stay_public(HttpMethod method, String path) {
    var status = statusOf("ANONYMOUS", method, path);

    assertEquals(false, status == 401 || status == 403, path + " answered " + status);
  }
}
