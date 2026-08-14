package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Promotion;
import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.StudentTrackChoiceRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
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
  @Autowired StudentRepository studentRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired StudentGroupAssignmentRepository assignmentRepository;
  @Autowired StudentTrackChoiceRepository trackChoiceRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String keyOf(Role role) {
    var apiKey = UUID.randomUUID().toString();
    appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash("hash")
            .role(role)
            .apiKey(apiKey)
            .build());
    return apiKey;
  }

  /** Writes are administered, so the tests already call them as an admin. */
  private String adminKey() {
    return keyOf(Role.ADMIN);
  }

  private String teacherKey() {
    return keyOf(Role.TEACHER);
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

  // --- authorization --------------------------------------------------------

  /** Reads back the generated key of a student, which the API deliberately never returns. */
  private String apiKeyOf(Student student) {
    return studentRepository.findById(student.getId()).orElseThrow().getUser().getApiKey();
  }

  @Test
  void reading_students_requires_authentication() {
    var response =
        restTemplate.exchange("/students", GET, new HttpEntity<>(new HttpHeaders()), String.class);

    assertEquals(UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void only_an_admin_can_write_students() {
    var student = created(adminKey(), persistedPromotionId());

    assertEquals(
        FORBIDDEN,
        putRaw(List.of(aStudent(persistedPromotionId())), apiKeyOf(student)).getStatusCode());
  }

  @Test
  void a_student_cannot_browse_the_whole_student_body() {
    var student = created(adminKey(), persistedPromotionId());

    var response =
        restTemplate.exchange(
            "/students", GET, new HttpEntity<>(bearer(apiKeyOf(student))), String.class);

    assertEquals(FORBIDDEN, response.getStatusCode());
  }

  @Test
  void a_student_reads_their_own_record() {
    var student = created(adminKey(), persistedPromotionId());

    var response =
        restTemplate.exchange(
            "/students/" + student.getId(),
            GET,
            new HttpEntity<>(bearer(apiKeyOf(student))),
            Student.class);

    assertEquals(OK, response.getStatusCode());
    assertEquals(student.getId(), response.getBody().getId());
  }

  @Test
  void a_student_cannot_read_another_student() {
    // The rule the subject states explicitly: a student never sees another student's data.
    var admin = adminKey();
    var jean = created(admin, persistedPromotionId());
    var alice = created(admin, persistedPromotionId());

    var response =
        restTemplate.exchange(
            "/students/" + alice.getId(),
            GET,
            new HttpEntity<>(bearer(apiKeyOf(jean))),
            String.class);

    assertEquals(FORBIDDEN, response.getStatusCode());
    assertTrue(response.getBody().contains("ForbiddenException"), "body was " + response.getBody());
  }

  @Test
  void a_teacher_reads_any_student() {
    var student = created(adminKey(), persistedPromotionId());

    var response =
        restTemplate.exchange(
            "/students/" + student.getId(),
            GET,
            new HttpEntity<>(bearer(teacherKey())),
            Student.class);

    assertEquals(OK, response.getStatusCode());
  }

  // --- computed fields ------------------------------------------------------

  private com.exam.hei.repository.model.Group persistedGroup(
      UUID promotionId, com.exam.hei.repository.model.Track track) {
    return groupRepository.save(
        com.exam.hei.repository.model.Group.builder()
            .ref(rand(10))
            .promotion(promotionRepository.findById(promotionId).orElseThrow())
            .track(track)
            .build());
  }

  private void assignTo(Student student, com.exam.hei.repository.model.Group group, String from) {
    assignmentRepository.save(
        com.exam.hei.repository.model.StudentGroupAssignment.builder()
            .student(studentRepository.findById(student.getId()).orElseThrow())
            .group(group)
            .startDate(LocalDate.parse(from))
            .build());
  }

  private void makeFollow(Student student, com.exam.hei.repository.model.Track track) {
    trackChoiceRepository.save(
        com.exam.hei.repository.model.StudentTrackChoice.builder()
            .student(studentRepository.findById(student.getId()).orElseThrow())
            .track(track)
            .fromSemester(semesterRepository.findByRef(SemesterRef.S4).orElseThrow())
            .build());
  }

  @Test
  void a_student_exposes_the_group_they_currently_belong_to() {
    var admin = adminKey();
    var promotionId = persistedPromotionId();
    var student = created(admin, promotionId);
    var k1 = persistedGroup(promotionId, null);
    assignTo(student, k1, "2025-09-01");

    var found =
        restTemplate
            .exchange(
                "/students/" + student.getId(), GET, new HttpEntity<>(bearer(admin)), Student.class)
            .getBody();

    assertEquals(k1.getId(), found.getCurrentGroup().getId());
  }

  @Test
  void a_student_still_in_the_common_core_exposes_no_track() {
    var admin = adminKey();
    var student = created(admin, persistedPromotionId());

    var found =
        restTemplate
            .exchange(
                "/students/" + student.getId(), GET, new HttpEntity<>(bearer(admin)), Student.class)
            .getBody();

    assertNull(found.getCurrentTrack());
    assertNull(found.getCurrentGroup());
  }

  @Test
  void a_student_exposes_the_track_they_follow() {
    var admin = adminKey();
    var student = created(admin, persistedPromotionId());
    makeFollow(student, trackRepository.findByCode("EL").orElseThrow());

    var found =
        restTemplate
            .exchange(
                "/students/" + student.getId(), GET, new HttpEntity<>(bearer(admin)), Student.class)
            .getBody();

    assertEquals("EL", found.getCurrentTrack().getCode());
  }

  // --- filters --------------------------------------------------------------

  @Test
  void students_can_be_filtered_by_the_group_they_were_in_on_a_date() {
    var admin = adminKey();
    var promotionId = persistedPromotionId();
    var jean = created(admin, promotionId);
    var alice = created(admin, promotionId);
    var k1 = persistedGroup(promotionId, null);
    assignTo(jean, k1, "2025-09-01");
    assignTo(alice, persistedGroup(promotionId, null), "2025-09-01");

    var inK1 =
        restTemplate.exchange(
            "/students?group_id=" + k1.getId() + "&at=2025-10-01",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Student>>() {});

    assertEquals(1, inK1.getBody().size());
    assertEquals(jean.getId(), inK1.getBody().get(0).getId());
  }

  @Test
  void a_date_before_the_assignment_returns_nobody() {
    var admin = adminKey();
    var promotionId = persistedPromotionId();
    var jean = created(admin, promotionId);
    var k1 = persistedGroup(promotionId, null);
    assignTo(jean, k1, "2025-09-01");

    var before =
        restTemplate.exchange(
            "/students?group_id=" + k1.getId() + "&at=2025-01-01",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Student>>() {});

    assertTrue(before.getBody().isEmpty());
  }

  @Test
  void students_can_be_filtered_by_the_track_they_follow() {
    var admin = adminKey();
    var promotionId = persistedPromotionId();
    var jean = created(admin, promotionId);
    created(admin, promotionId);
    makeFollow(jean, trackRepository.findByCode("EL").orElseThrow());

    var followingEl =
        restTemplate.exchange(
            "/students?track_code=EL&page_size=500",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Student>>() {});

    assertTrue(
        followingEl.getBody().stream().anyMatch(s -> s.getId().equals(jean.getId())),
        "expected Jean among the EL students");
    assertTrue(
        followingEl.getBody().stream().allMatch(s -> "EL".equals(s.getCurrentTrack().getCode())));
  }
}
