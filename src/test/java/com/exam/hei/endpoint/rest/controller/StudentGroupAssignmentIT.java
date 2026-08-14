package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.StudentGroupAssignment;
import com.exam.hei.endpoint.rest.model.StudentGroupChange;
import com.exam.hei.endpoint.rest.model.StudentTrackChoiceCreation;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Group;
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

/** Walks the scenario of the assignment over HTTP, then the rules that constrain it. */
class StudentGroupAssignmentIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired TrackRepository trackRepository;

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

  private Promotion promotion;

  private Promotion promotion() {
    if (promotion == null) {
      promotion =
          promotionRepository.save(
              Promotion.builder()
                  .ref(rand(5))
                  .name("Promotion under test")
                  .startYear(2025)
                  .endYear(2028)
                  .tracks(List.of(el(), tn()))
                  .build());
    }
    return promotion;
  }

  private Group group(Track track) {
    return groupRepository.save(
        Group.builder().ref(rand(10)).promotion(promotion()).track(track).build());
  }

  private Student student() {
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .apiKey(UUID.randomUUID().toString())
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion())
            .user(account)
            .build());
  }

  private ResponseEntity<StudentGroupAssignment> move(
      Student student, Group group, String startDate, String apiKey) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/group-assignments",
        POST,
        new HttpEntity<>(
            StudentGroupChange.builder()
                .groupId(group.getId())
                .startDate(LocalDate.parse(startDate))
                .build(),
            bearer(apiKey)),
        StudentGroupAssignment.class);
  }

  private ResponseEntity<String> moveRaw(
      Student student, Group group, String startDate, String apiKey) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/group-assignments",
        POST,
        new HttpEntity<>(
            StudentGroupChange.builder()
                .groupId(group.getId())
                .startDate(LocalDate.parse(startDate))
                .build(),
            bearer(apiKey)),
        String.class);
  }

  private List<StudentGroupAssignment> history(Student student, String apiKey) {
    return restTemplate
        .exchange(
            "/students/" + student.getId() + "/group-assignments",
            GET,
            new HttpEntity<>(bearer(apiKey)),
            new ParameterizedTypeReference<List<StudentGroupAssignment>>() {})
        .getBody();
  }

  private void chooses(Student student, Track track, String apiKey) {
    restTemplate.exchange(
        "/students/" + student.getId() + "/track-choices",
        POST,
        new HttpEntity<>(
            StudentTrackChoiceCreation.builder()
                .trackId(track.getId())
                .fromSemesterRef(SemesterRef.S4)
                .build(),
            bearer(apiKey)),
        String.class);
  }

  // --- the scenario ---------------------------------------------------------

  @Test
  void a_student_goes_through_four_groups_and_keeps_the_whole_history() {
    var admin = adminKey();
    var jean = student();
    var k1 = group(null);
    var k2 = group(null);
    var k3 = group(null);

    assertEquals(OK, move(jean, k1, "2025-09-01", admin).getStatusCode());
    assertEquals(OK, move(jean, k2, "2025-11-15", admin).getStatusCode());
    assertEquals(OK, move(jean, k3, "2026-02-10", admin).getStatusCode());
    assertEquals(OK, move(jean, k1, "2026-04-20", admin).getStatusCode());

    var history = history(jean, admin);

    assertEquals(4, history.size());
    assertEquals(LocalDate.parse("2025-11-14"), history.get(0).getEndDate());
    assertEquals(LocalDate.parse("2026-02-09"), history.get(1).getEndDate());
    assertEquals(LocalDate.parse("2026-04-19"), history.get(2).getEndDate());
    assertNull(history.get(3).getEndDate());
  }

  @Test
  void the_group_of_a_student_on_a_given_date_is_readable() {
    var admin = adminKey();
    var jean = student();
    var k1 = group(null);
    var k2 = group(null);
    move(jean, k1, "2025-09-01", admin);
    move(jean, k2, "2025-11-15", admin);

    var onTheFirstOfOctober =
        restTemplate.exchange(
            "/students/" + jean.getId() + "/group-assignments?at=2025-10-01",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<StudentGroupAssignment>>() {});

    assertEquals(1, onTheFirstOfOctober.getBody().size());
    assertEquals(k1.getId(), onTheFirstOfOctober.getBody().get(0).getGroup().getId());
  }

  @Test
  void a_date_before_any_assignment_returns_nothing() {
    var admin = adminKey();
    var jean = student();
    move(jean, group(null), "2025-09-01", admin);

    var before =
        restTemplate.exchange(
            "/students/" + jean.getId() + "/group-assignments?at=2025-01-01",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<StudentGroupAssignment>>() {});

    assertTrue(before.getBody().isEmpty());
  }

  // --- track consistency ----------------------------------------------------

  @Test
  void a_student_without_a_track_cannot_enter_a_track_group() {
    var admin = adminKey();
    var jean = student();

    var response = moveRaw(jean, group(el()), "2025-09-01", admin);

    assertEquals(CONFLICT, response.getStatusCode());
    assertTrue(response.getBody().contains("TRACK_NOT_SELECTED"), "body was " + response.getBody());
  }

  @Test
  void an_el_student_moves_between_el_groups_but_never_into_a_tn_one() {
    var admin = adminKey();
    var jean = student();
    move(jean, group(null), "2025-09-01", admin);
    chooses(jean, el(), admin);

    assertEquals(OK, move(jean, group(el()), "2026-09-01", admin).getStatusCode());
    assertEquals(OK, move(jean, group(el()), "2026-11-15", admin).getStatusCode());
    assertEquals(CONFLICT, moveRaw(jean, group(tn()), "2027-01-10", admin).getStatusCode());
  }

  @Test
  void a_student_who_chose_cannot_go_back_to_a_common_core_group() {
    var admin = adminKey();
    var jean = student();
    move(jean, group(null), "2025-09-01", admin);
    chooses(jean, el(), admin);
    move(jean, group(el()), "2026-09-01", admin);

    assertEquals(CONFLICT, moveRaw(jean, group(null), "2026-11-15", admin).getStatusCode());
  }

  @Test
  void a_change_starting_before_the_current_assignment_is_refused() {
    var admin = adminKey();
    var jean = student();
    move(jean, group(null), "2025-11-15", admin);

    assertEquals(400, moveRaw(jean, group(null), "2025-09-01", admin).getStatusCode().value());
  }

  @Test
  void a_refused_change_leaves_the_history_untouched() {
    var admin = adminKey();
    var jean = student();
    move(jean, group(null), "2025-09-01", admin);

    moveRaw(jean, group(el()), "2026-09-01", admin);

    assertEquals(1, history(jean, admin).size());
    assertNull(history(jean, admin).get(0).getEndDate());
  }

  // --- authorization --------------------------------------------------------

  @Test
  void reading_a_history_requires_authentication() {
    var jean = student();

    assertEquals(
        UNAUTHORIZED,
        restTemplate
            .exchange(
                "/students/" + jean.getId() + "/group-assignments",
                GET,
                new HttpEntity<>(bearer(null)),
                String.class)
            .getStatusCode());
  }

  @Test
  void a_student_reads_their_own_history() {
    var admin = adminKey();
    var jean = student();
    move(jean, group(null), "2025-09-01", admin);
    var jeanKey = studentRepository.findById(jean.getId()).orElseThrow().getUser().getApiKey();

    assertEquals(1, history(jean, jeanKey).size());
  }

  @Test
  void a_student_cannot_read_the_history_of_another_student() {
    var admin = adminKey();
    var jean = student();
    var alice = student();
    move(jean, group(null), "2025-09-01", admin);
    var aliceKey = studentRepository.findById(alice.getId()).orElseThrow().getUser().getApiKey();

    var response =
        restTemplate.exchange(
            "/students/" + jean.getId() + "/group-assignments",
            GET,
            new HttpEntity<>(bearer(aliceKey)),
            String.class);

    assertEquals(403, response.getStatusCode().value());
  }
}
