package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Promotion;
import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.StudentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class StudentIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  /** Writes are administered, so the tests already call them as an admin. */
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

  private UUID persistedPromotionId() {
    return promotionRepository
        .save(
            com.exam.hei.repository.model.Promotion.builder()
                .ref(rand(5))
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build())
        .getId();
  }

  private Student aStudent(UUID promotionId) {
    return Student.builder()
        .ref(rand(20))
        .firstName("Jean")
        .lastName("Rakoto")
        .email(rand(12) + "@hei.test")
        .birthDate(LocalDate.of(2004, 3, 12))
        .entranceDate(LocalDate.of(2025, 9, 1))
        .promotion(Promotion.builder().id(promotionId).build())
        .build();
  }

  private ResponseEntity<List<Student>> put(List<Student> body, String apiKey) {
    return restTemplate.exchange(
        "/students",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> putRaw(List<Student> body, String apiKey) {
    return restTemplate.exchange(
        "/students", PUT, new HttpEntity<>(body, bearer(apiKey)), String.class);
  }

  private Student created(String adminKey, UUID promotionId) {
    return put(List.of(aStudent(promotionId)), adminKey).getBody().get(0);
  }

  // --- crupdate then read ---------------------------------------------------

  @Test
  void a_student_is_created_then_readable_by_its_id() {
    var admin = adminKey();
    var student = created(admin, persistedPromotionId());

    assertNotNull(student.getId());

    var found =
        restTemplate.exchange(
            "/students/" + student.getId(), GET, new HttpEntity<>(bearer(admin)), Student.class);

    assertEquals(OK, found.getStatusCode());
    assertEquals(student.getRef(), found.getBody().getRef());
    assertEquals("Jean", found.getBody().getFirstName());
    assertEquals(LocalDate.of(2025, 9, 1), found.getBody().getEntranceDate());
  }

  @Test
  void a_created_student_is_active_and_carries_its_promotion() {
    var promotionId = persistedPromotionId();

    var student = created(adminKey(), promotionId);

    assertEquals(StudentStatus.ACTIVE, student.getStatus());
    assertEquals(promotionId, student.getPromotion().getId());
  }

  @Test
  void a_student_is_updated_rather_than_duplicated() {
    var admin = adminKey();
    var student = created(admin, persistedPromotionId());
    student.setLastName("Randrianarisoa");

    var updated = put(List.of(student), admin).getBody().get(0);

    assertEquals(student.getId(), updated.getId());
    assertEquals("Randrianarisoa", updated.getLastName());
  }

  @Test
  void an_unknown_student_id_is_not_found() {
    var response =
        restTemplate.exchange(
            "/students/" + UUID.randomUUID(),
            GET,
            new HttpEntity<>(bearer(adminKey())),
            String.class);

    assertEquals(NOT_FOUND, response.getStatusCode());
    assertTrue(response.getBody().contains("NotFoundException"), "body was " + response.getBody());
  }

  @Test
  void a_student_naming_an_unknown_promotion_is_not_found() {
    var orphan = aStudent(UUID.randomUUID());

    assertEquals(NOT_FOUND, putRaw(List.of(orphan), adminKey()).getStatusCode());
  }

  // --- listing --------------------------------------------------------------

  @Test
  void students_can_be_filtered_by_promotion() {
    var admin = adminKey();
    var promotionId = persistedPromotionId();
    created(admin, promotionId);
    created(admin, promotionId);
    created(admin, persistedPromotionId());

    var page =
        restTemplate.exchange(
            "/students?promotion_id=" + promotionId,
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Student>>() {});

    assertEquals(2, page.getBody().size());
    assertTrue(page.getBody().stream().allMatch(s -> s.getPromotion().getId().equals(promotionId)));
  }

  @Test
  void page_size_limits_the_number_of_returned_students() {
    var admin = adminKey();
    var promotionId = persistedPromotionId();
    created(admin, promotionId);
    created(admin, promotionId);

    var page =
        restTemplate.exchange(
            "/students?promotion_id=" + promotionId + "&page=1&page_size=1",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Student>>() {});

    assertEquals(1, page.getBody().size());
  }

  @Test
  void an_invalid_page_is_a_bad_request() {
    var response =
        restTemplate.exchange(
            "/students?page=0", GET, new HttpEntity<>(bearer(adminKey())), String.class);

    assertEquals(400, response.getStatusCode().value());
  }

  // --- payload contract -----------------------------------------------------

  @Test
  void students_are_serialized_in_snake_case() {
    var admin = adminKey();
    var student = created(admin, persistedPromotionId());

    var raw =
        restTemplate
            .exchange(
                "/students/" + student.getId(), GET, new HttpEntity<>(bearer(admin)), String.class)
            .getBody();

    assertTrue(raw.contains("\"first_name\""), "body was " + raw);
    assertTrue(raw.contains("\"entrance_date\""), "body was " + raw);
    assertFalse(raw.contains("\"firstName\""), "body was " + raw);
  }

  @Test
  void a_student_payload_never_carries_the_credentials_of_its_account() {
    // Creating a student creates its account: the generated key must not leak into the response.
    var admin = adminKey();
    var student = created(admin, persistedPromotionId());

    var raw =
        restTemplate
            .exchange(
                "/students/" + student.getId(), GET, new HttpEntity<>(bearer(admin)), String.class)
            .getBody();

    assertFalse(raw.contains("api_key"), "body was " + raw);
    assertFalse(raw.contains("password"), "body was " + raw);
  }

  @Test
  void the_current_track_of_a_student_is_not_resolved_yet() {
    // Part of the contract, but it comes from a track choice, introduced by its own feature.
    assertNull(created(adminKey(), persistedPromotionId()).getCurrentTrack());
  }
}
