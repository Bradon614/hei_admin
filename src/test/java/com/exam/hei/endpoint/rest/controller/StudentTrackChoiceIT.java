package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.StudentTrackChoice;
import com.exam.hei.endpoint.rest.model.StudentTrackChoiceCreation;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Track;
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

class StudentTrackChoiceIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String adminKey() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.ADMIN)
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

  private Track el() {
    return trackRepository.findByCode("EL").orElseThrow();
  }

  private Track tn() {
    return trackRepository.findByCode("TN").orElseThrow();
  }

  private Student studentOfPromotionOpening(Track... tracks) {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .tracks(List.of(tracks))
                .build());
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(account)
            .build());
  }

  private ResponseEntity<StudentTrackChoice> choose(
      Student student, Track track, SemesterRef from, String apiKey) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/track-choices",
        POST,
        new HttpEntity<>(
            StudentTrackChoiceCreation.builder()
                .trackId(track.getId())
                .fromSemesterRef(from)
                .build(),
            bearer(apiKey)),
        StudentTrackChoice.class);
  }

  private ResponseEntity<String> chooseRaw(
      Student student, Track track, SemesterRef from, String apiKey) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/track-choices",
        POST,
        new HttpEntity<>(
            StudentTrackChoiceCreation.builder()
                .trackId(track.getId())
                .fromSemesterRef(from)
                .build(),
            bearer(apiKey)),
        String.class);
  }

  private List<StudentTrackChoice> choicesOf(Student student, String apiKey) {
    return restTemplate
        .exchange(
            "/students/" + student.getId() + "/track-choices",
            GET,
            new HttpEntity<>(bearer(apiKey)),
            new ParameterizedTypeReference<List<StudentTrackChoice>>() {})
        .getBody();
  }

  @Test
  void a_student_chooses_a_track_from_the_first_track_semester() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());

    var choice = choose(jean, el(), SemesterRef.S4, admin);

    assertEquals(OK, choice.getStatusCode());
    assertNotNull(choice.getBody().getId());
    assertEquals("EL", choice.getBody().getTrack().getCode());
    assertEquals(SemesterRef.S4, choice.getBody().getFromSemester().getRef());
    assertNotNull(choice.getBody().getDecidedAt());
  }

  @Test
  void a_choice_is_then_listed() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());
    choose(jean, el(), SemesterRef.S4, admin);

    assertEquals(1, choicesOf(jean, admin).size());
  }

  @Test
  void a_student_who_never_chose_has_no_choice_listed() {
    var admin = adminKey();

    assertTrue(choicesOf(studentOfPromotionOpening(el()), admin).isEmpty());
  }

  @Test
  void a_track_is_never_chosen_for_a_common_core_semester() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());

    var response = chooseRaw(jean, el(), SemesterRef.S1, admin);

    assertEquals(400, response.getStatusCode().value());
    assertTrue(response.getBody().contains("common core"), "body was " + response.getBody());
  }

  @Test
  void a_first_choice_cannot_start_after_tracks_begin() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());

    assertEquals(400, chooseRaw(jean, el(), SemesterRef.S5, admin).getStatusCode().value());
  }

  @Test
  void a_reorientation_starts_later_and_leaves_the_first_choice_in_place() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());
    choose(jean, el(), SemesterRef.S4, admin);

    assertEquals(OK, choose(jean, tn(), SemesterRef.S5, admin).getStatusCode());

    var choices = choicesOf(jean, admin);
    assertEquals(2, choices.size());
    assertEquals("EL", choices.get(0).getTrack().getCode());
    assertEquals("TN", choices.get(1).getTrack().getCode());
  }

  @Test
  void choosing_twice_from_the_same_semester_is_a_conflict() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());
    choose(jean, el(), SemesterRef.S4, admin);

    assertEquals(CONFLICT, chooseRaw(jean, tn(), SemesterRef.S4, admin).getStatusCode());
  }

  @Test
  void a_track_the_promotion_does_not_open_is_refused() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el());

    assertEquals(400, chooseRaw(jean, tn(), SemesterRef.S4, admin).getStatusCode().value());
  }

  @Test
  void reading_the_choices_requires_authentication() {
    var jean = studentOfPromotionOpening(el());

    assertEquals(
        UNAUTHORIZED,
        restTemplate
            .exchange(
                "/students/" + jean.getId() + "/track-choices",
                GET,
                new HttpEntity<>(bearer(null)),
                String.class)
            .getStatusCode());
  }

  @Test
  void only_an_admin_can_record_a_choice() {
    var jean = studentOfPromotionOpening(el(), tn());
    var jeanKey =
        jwtService.issue(studentRepository.findById(jean.getId()).orElseThrow().getUser()).token();

    assertEquals(403, chooseRaw(jean, el(), SemesterRef.S4, jeanKey).getStatusCode().value());
  }

  @Test
  void a_student_cannot_read_the_choices_of_another_student() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());
    var alice = studentOfPromotionOpening(el(), tn());
    choose(jean, el(), SemesterRef.S4, admin);
    var aliceKey =
        jwtService.issue(studentRepository.findById(alice.getId()).orElseThrow().getUser()).token();

    var response =
        restTemplate.exchange(
            "/students/" + jean.getId() + "/track-choices",
            GET,
            new HttpEntity<>(bearer(aliceKey)),
            String.class);

    assertEquals(403, response.getStatusCode().value());
  }

  @Test
  void choices_are_serialized_in_snake_case() {
    var admin = adminKey();
    var jean = studentOfPromotionOpening(el(), tn());
    choose(jean, el(), SemesterRef.S4, admin);

    var raw =
        restTemplate
            .exchange(
                "/students/" + jean.getId() + "/track-choices",
                GET,
                new HttpEntity<>(bearer(admin)),
                String.class)
            .getBody();

    assertTrue(raw.contains("\"from_semester\""), "body was " + raw);
    assertTrue(raw.contains("\"student_id\""), "body was " + raw);
    assertTrue(raw.contains("\"common_core\""), "body was " + raw);
  }
}
