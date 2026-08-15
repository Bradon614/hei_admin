package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.GradeChange;
import com.exam.hei.endpoint.rest.model.StudentResult;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.model.GraduationBlockerCode;
import com.exam.hei.model.SemesterResultStatus;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.StudentTrackChoiceRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentTrackChoice;
import java.math.BigDecimal;
import java.time.Instant;
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

class ResultIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired ExamRepository examRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired StudentTrackChoiceRepository studentTrackChoiceRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String tokenFor(Role role) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(role)
                .build());
    return jwtService.issue(user).token();
  }

  private Promotion promotion() {
    return promotionRepository.save(
        Promotion.builder()
            .ref(rand(5))
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .tracks(List.of(trackRepository.findByCode("EL").orElseThrow()))
            .build());
  }

  private record StudentAccount(Student student, String token) {}

  private StudentAccount studentAccount(Promotion promotion) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    var student =
        studentRepository.save(
            Student.builder()
                .ref(rand(20))
                .firstName("Jean")
                .lastName("Rakoto")
                .email(rand(12) + "@hei.test")
                .entranceDate(LocalDate.of(2025, 9, 1))
                .promotion(promotion)
                .user(user)
                .build());
    return new StudentAccount(student, jwtService.issue(user).token());
  }

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
    }
    return headers;
  }

  /** One course worth the whole 30 required credits of the semester, one exam. */
  private Exam soleExamOf(SemesterRef semesterRef) {
    var course =
        courseRepository.save(
            Course.builder()
                .ref(rand(20))
                .title("Course under test")
                .credits(30)
                .semester(semesterRepository.findByRef(semesterRef).orElseThrow())
                .build());
    return examRepository.save(
        Exam.builder()
            .course(course)
            .title("Final")
            .dateExam(Instant.parse("2026-01-15T08:00:00Z"))
            .coefficient(new BigDecimal("1.00"))
            .build());
  }

  private void grade(Exam exam, UUID studentId, String value, String admin) {
    restTemplate.exchange(
        "/exams/" + exam.getId() + "/grades",
        PUT,
        new HttpEntity<>(
            List.of(
                GradeChange.builder()
                    .studentId(studentId)
                    .value(new BigDecimal(value))
                    .reasonType(GradeChangeReasonType.CREATION)
                    .reason("Initial entry")
                    .build()),
            bearer(admin)),
        String.class);
  }

  private void chooseElFromS4(Student student) {
    studentTrackChoiceRepository.save(
        StudentTrackChoice.builder()
            .student(student)
            .track(trackRepository.findByCode("EL").orElseThrow())
            .fromSemester(semesterRepository.findByRef(SemesterRef.S4).orElseThrow())
            .build());
  }

  private ResponseEntity<StudentResult> result(Student student, String token) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/result",
        GET,
        new HttpEntity<>(bearer(token)),
        StudentResult.class);
  }

  private ResponseEntity<String> resultRaw(Student student, String token) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/result",
        GET,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  private ResponseEntity<List<StudentResult>> promotionResults(
      Promotion promotion, String trackCode, String token) {
    var url =
        "/promotions/"
            + promotion.getId()
            + "/results"
            + (trackCode == null ? "" : "?track_code=" + trackCode);
    return restTemplate.exchange(
        url, GET, new HttpEntity<>(bearer(token)), new ParameterizedTypeReference<>() {});
  }

  // --- basics ---------------------------------------------------------------------

  @Test
  void reading_a_result_requires_authentication() {
    var student = studentAccount(promotion()).student();

    assertEquals(UNAUTHORIZED, resultRaw(student, null).getStatusCode());
  }

  @Test
  void a_student_reads_their_own_result() {
    var admin = tokenFor(Role.ADMIN);
    var account = studentAccount(promotion());
    var exam = soleExamOf(SemesterRef.S1);
    grade(exam, account.student().getId(), "12.00", admin);

    assertEquals(OK, result(account.student(), account.token()).getStatusCode());
  }

  @Test
  void a_student_cannot_read_another_student_s_result() {
    var account = studentAccount(promotion());
    var another = studentAccount(promotion());

    assertEquals(403, resultRaw(account.student(), another.token()).getStatusCode().value());
  }

  // --- the S1-S6 computation, end to end -------------------------------------------

  @Test
  void a_student_who_validates_all_six_semesters_graduates() {
    var admin = tokenFor(Role.ADMIN);
    var account = studentAccount(promotion());
    chooseElFromS4(account.student());

    for (var ref : SemesterRef.values()) {
      grade(soleExamOf(ref), account.student().getId(), "14.00", admin);
    }

    var found = result(account.student(), admin).getBody();

    assertTrue(found.isGraduated());
    assertEquals(180, found.getTotalObtainedCredits());
    assertTrue(found.getBlockers().isEmpty());
    assertEquals(6, found.getSemesterResults().size());
  }

  @Test
  void a_student_without_a_track_choice_from_s4_is_blocked_by_track_not_selected() {
    var admin = tokenFor(Role.ADMIN);
    var account = studentAccount(promotion());
    for (var ref : List.of(SemesterRef.S1, SemesterRef.S2, SemesterRef.S3)) {
      grade(soleExamOf(ref), account.student().getId(), "14.00", admin);
    }
    // No track choice: S4, S5, S6 are not evaluable.

    var found = result(account.student(), admin).getBody();

    assertFalse(found.isGraduated());
    var s4Result =
        found.getSemesterResults().stream()
            .filter(sr -> sr.getSemester().getRef() == SemesterRef.S4)
            .findFirst()
            .orElseThrow();
    assertEquals(SemesterResultStatus.TRACK_NOT_SELECTED, s4Result.getStatus());
    assertTrue(
        found.getBlockers().stream()
            .anyMatch(
                b ->
                    b.getCode() == GraduationBlockerCode.TRACK_NOT_SELECTED
                        && b.getSemesterRef() == SemesterRef.S4));
  }

  @Test
  void a_failed_semester_is_blocked_by_semester_not_validated() {
    var admin = tokenFor(Role.ADMIN);
    var account = studentAccount(promotion());
    var exam = soleExamOf(SemesterRef.S1);
    grade(exam, account.student().getId(), "5.00", admin); // below 10, course and semester fail

    var found = result(account.student(), admin).getBody();

    assertFalse(found.isGraduated());
    assertTrue(
        found.getBlockers().stream()
            .anyMatch(
                b ->
                    b.getCode() == GraduationBlockerCode.SEMESTER_NOT_VALIDATED
                        && b.getSemesterRef() == SemesterRef.S1));
  }

  // --- promotion listing ----------------------------------------------------------

  @Test
  void only_an_admin_reads_a_promotion_s_results() {
    var promotion = promotion();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          restTemplate
              .exchange(
                  "/promotions/" + promotion.getId() + "/results",
                  GET,
                  new HttpEntity<>(bearer(tokenFor(role))),
                  String.class)
              .getStatusCode()
              .value(),
          "role " + role);
    }
  }

  @Test
  void a_promotion_s_results_list_every_student() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    studentAccount(promotion);
    studentAccount(promotion);

    var found = promotionResults(promotion, null, admin).getBody();

    assertEquals(2, found.size());
  }

  @Test
  void a_track_filter_excludes_common_core_students() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var chose = studentAccount(promotion);
    chooseElFromS4(chose.student());
    studentAccount(promotion); // still in the common core, no track choice

    var found = promotionResults(promotion, "EL", admin).getBody();

    assertEquals(1, found.size());
    assertEquals(chose.student().getId(), found.get(0).getStudent().getId());
  }

  @Test
  void results_are_serialized_in_snake_case() {
    var admin = tokenFor(Role.ADMIN);
    var account = studentAccount(promotion());
    grade(soleExamOf(SemesterRef.S1), account.student().getId(), "12.00", admin);

    var raw = resultRaw(account.student(), admin).getBody();

    assertTrue(raw.contains("\"general_average\""), "body was " + raw);
    assertTrue(raw.contains("\"obtained_credits\""), "body was " + raw);
  }
}
