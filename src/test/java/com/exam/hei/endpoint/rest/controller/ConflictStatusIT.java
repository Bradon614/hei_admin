package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.Promotion;
import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.endpoint.rest.model.Teacher;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class ConflictStatusIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String adminToken() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .build());
    return jwtService.issue(user).token();
  }

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + token);
    return headers;
  }

  private ResponseEntity<String> put(String path, Object body, String token) {
    return restTemplate.exchange(path, PUT, new HttpEntity<>(body, bearer(token)), String.class);
  }

  private UUID persistedPromotionId() {
    return promotionRepository
        .save(
            com.exam.hei.repository.model.Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build())
        .getId();
  }

  private static Student aStudent(UUID promotionId, String email) {
    return Student.builder()
        .ref(rand(20))
        .firstName("Jean")
        .lastName("Rakoto")
        .email(email)
        .password("s3cret!!")
        .entranceDate(LocalDate.of(2025, 9, 1))
        .promotion(Promotion.builder().id(promotionId).build())
        .build();
  }

  private static Teacher aTeacher(String email) {
    return Teacher.builder()
        .ref(rand(20))
        .firstName("Aina")
        .lastName("Randria")
        .email(email)
        .password("s3cret!!")
        .build();
  }

  private static Promotion aPromotion(String ref) {
    return Promotion.builder()
        .ref(ref)
        .name("Promotion under test")
        .startYear(2025)
        .endYear(2028)
        .build();
  }

  @Test
  void a_duplicate_student_email_answers_409() {
    var admin = adminToken();
    var promotionId = persistedPromotionId();
    var email = rand(12) + "@hei.test";
    assertEquals(
        OK, put("/students", List.of(aStudent(promotionId, email)), admin).getStatusCode());

    var again = put("/students", List.of(aStudent(promotionId, email)), admin);

    assertEquals(CONFLICT, again.getStatusCode(), "body was " + again.getBody());
  }

  @Test
  void a_duplicate_teacher_email_answers_409() {
    var admin = adminToken();
    var email = rand(12) + "@hei.test";
    assertEquals(OK, put("/teachers", List.of(aTeacher(email)), admin).getStatusCode());

    var again = put("/teachers", List.of(aTeacher(email)), admin);

    assertEquals(CONFLICT, again.getStatusCode(), "body was " + again.getBody());
  }

  @Test
  void a_duplicate_promotion_reference_answers_409() {
    var admin = adminToken();
    var ref = TestRefs.promotionRef();
    assertEquals(OK, put("/promotions", List.of(aPromotion(ref)), admin).getStatusCode());

    var again = put("/promotions", List.of(aPromotion(ref)), admin);

    assertEquals(CONFLICT, again.getStatusCode(), "body was " + again.getBody());
  }

  @Test
  void the_conflict_carries_the_error_payload_of_the_specification() {
    var admin = adminToken();
    var ref = TestRefs.promotionRef();
    put("/promotions", List.of(aPromotion(ref)), admin);

    var body = put("/promotions", List.of(aPromotion(ref)), admin).getBody();

    assertTrue(body.contains("ConflictException"), "body was " + body);
    assertTrue(body.contains("already taken"), "body was " + body);
  }

  @Test
  void the_conflict_message_leaks_no_constraint_name() {
    var admin = adminToken();
    var ref = TestRefs.promotionRef();
    put("/promotions", List.of(aPromotion(ref)), admin);

    var body = put("/promotions", List.of(aPromotion(ref)), admin).getBody();

    assertTrue(!body.contains("constraint"), "body was " + body);
    assertTrue(!body.contains("Detail:"), "body was " + body);
  }

  @Test
  void a_reference_too_long_for_its_column_answers_400() {
    var admin = adminToken();

    var response = put("/promotions", List.of(aPromotion("TOOLONG")), admin);

    assertEquals(BAD_REQUEST, response.getStatusCode(), "body was " + response.getBody());
    assertTrue(response.getBody().contains("BadRequestException"));
  }
}
