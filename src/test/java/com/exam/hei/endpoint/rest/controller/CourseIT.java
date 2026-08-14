package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Course;
import com.exam.hei.endpoint.rest.model.Semester;
import com.exam.hei.endpoint.rest.model.Track;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class CourseIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String keyOf(Role role) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(role)
                .build());
    return jwtService.issue(user).token();
  }

  private static HttpHeaders bearer(String apiKey) {
    var headers = new HttpHeaders();
    if (apiKey != null) {
      headers.set(AUTHORIZATION, "Bearer " + apiKey);
    }
    return headers;
  }

  private UUID elId() {
    return trackRepository.findByCode("EL").orElseThrow().getId();
  }

  private static Course aCourse(SemesterRef semesterRef, UUID trackId, int credits) {
    return Course.builder()
        .ref(rand(20))
        .title("Course under test")
        .credits(credits)
        .semester(Semester.builder().ref(semesterRef).build())
        .track(trackId == null ? null : Track.builder().id(trackId).build())
        .build();
  }

  private ResponseEntity<List<Course>> put(List<Course> body, String apiKey) {
    return restTemplate.exchange(
        "/courses",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> putRaw(List<Course> body, String apiKey) {
    return restTemplate.exchange(
        "/courses", PUT, new HttpEntity<>(body, bearer(apiKey)), String.class);
  }

  @Test
  void listing_courses_requires_authentication() {
    assertEquals(
        UNAUTHORIZED,
        restTemplate
            .exchange("/courses", GET, new HttpEntity<>(bearer(null)), String.class)
            .getStatusCode());
  }

  @Test
  void a_course_is_created_then_readable_by_its_id() {
    var admin = keyOf(Role.ADMIN);

    var created = put(List.of(aCourse(SemesterRef.S1, null, 6)), admin).getBody().get(0);

    assertNotNull(created.getId());
    assertEquals(SemesterRef.S1, created.getSemester().getRef());
    assertNull(created.getTrack());

    var found =
        restTemplate.exchange(
            "/courses/" + created.getId(), GET, new HttpEntity<>(bearer(admin)), Course.class);

    assertEquals(OK, found.getStatusCode());
    assertEquals(6, found.getBody().getCredits());
  }

  @Test
  void a_semester_is_named_by_its_reference() {
    // Semesters are reference data: S5 is what a human writes, and the enum makes it typo proof.
    var created =
        put(List.of(aCourse(SemesterRef.S5, null, 12)), keyOf(Role.ADMIN)).getBody().get(0);

    assertEquals(SemesterRef.S5, created.getSemester().getRef());
  }

  @Test
  void a_track_specific_course_carries_its_track() {
    var created =
        put(List.of(aCourse(SemesterRef.S5, elId(), 18)), keyOf(Role.ADMIN)).getBody().get(0);

    assertEquals("EL", created.getTrack().getCode());
  }

  @Test
  void a_common_core_semester_refuses_a_track_specific_course() {
    var response = putRaw(List.of(aCourse(SemesterRef.S2, elId(), 6)), keyOf(Role.ADMIN));

    assertEquals(400, response.getStatusCode().value());
    assertTrue(response.getBody().contains("common core"), "body was " + response.getBody());
  }

  @Test
  void a_course_is_worth_at_least_one_credit() {
    assertEquals(
        400,
        putRaw(List.of(aCourse(SemesterRef.S1, null, 0)), keyOf(Role.ADMIN))
            .getStatusCode()
            .value());
  }

  @Test
  void an_unknown_course_id_is_not_found() {
    assertEquals(
        NOT_FOUND,
        restTemplate
            .exchange(
                "/courses/" + UUID.randomUUID(),
                GET,
                new HttpEntity<>(bearer(keyOf(Role.ADMIN))),
                String.class)
            .getStatusCode());
  }

  @Test
  void only_an_admin_can_write_courses() {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          putRaw(List.of(aCourse(SemesterRef.S1, null, 6)), keyOf(role)).getStatusCode().value(),
          "role " + role);
    }
  }

  @Test
  void courses_can_be_filtered_by_semester_and_track_together() {
    var admin = keyOf(Role.ADMIN);
    var common = put(List.of(aCourse(SemesterRef.S4, null, 12)), admin).getBody().get(0);
    var forEl = put(List.of(aCourse(SemesterRef.S4, elId(), 18)), admin).getBody().get(0);

    var filtered =
        restTemplate.exchange(
            "/courses?semester_ref=S4&track_code=EL&page_size=500",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Course>>() {});

    var ids = filtered.getBody().stream().map(Course::getId).toList();
    assertTrue(ids.contains(common.getId()), "a common course is followed by every track");
    assertTrue(ids.contains(forEl.getId()));
  }

  @Test
  void courses_are_serialized_in_snake_case() {
    var admin = keyOf(Role.ADMIN);
    var created = put(List.of(aCourse(SemesterRef.S1, null, 6)), admin).getBody().get(0);

    var raw =
        restTemplate
            .exchange(
                "/courses/" + created.getId(), GET, new HttpEntity<>(bearer(admin)), String.class)
            .getBody();

    assertTrue(raw.contains("\"common_core\""), "body was " + raw);
    assertTrue(raw.contains("\"sem_order\""), "body was " + raw);
  }
}
