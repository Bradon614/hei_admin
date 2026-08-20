package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.GradeHistoryRepository;
import com.exam.hei.repository.GradeRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class GradeAdminPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired ExamRepository examRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired GradeRepository gradeRepository;
  @Autowired GradeHistoryRepository gradeHistoryRepository;
  @Autowired JwtService jwtService;

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(NEVER).build();

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

  private static String form(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .reduce((a, b) -> a + "&" + b)
        .orElse("");
  }

  private HttpResponse<String> post(String path, Map<String, String> fields, String cookieToken)
      throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(fields)));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String cookieToken) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(restTemplate.getRootUri() + path));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private Promotion promotion() {
    return promotionRepository.save(
        Promotion.builder()
            .ref(TestRefs.promotionRef())
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .build());
  }

  private Student studentOf(Promotion promotion) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(18))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(user)
            .build());
  }

  private Exam examOn(SemesterRef semesterRef) {
    var course =
        courseRepository.save(
            Course.builder()
                .ref(rand(18))
                .title("Course under test")
                .credits(6)
                .semester(semesterRepository.findByRef(semesterRef).orElseThrow())
                .build());
    return examRepository.save(
        Exam.builder()
            .course(course)
            .title("Final exam")
            .dateExam(Instant.parse("2026-01-15T00:00:00Z"))
            .coefficient(BigDecimal.ONE)
            .build());
  }

  private Map<String, String> entry(
      Student student, Promotion promotion, String value, String reasonType, String reason) {
    return Map.of(
        "student_id", student.getId().toString(),
        "promotion_id", promotion.getId().toString(),
        "value", value,
        "reason_type", reasonType,
        "reason", reason);
  }

  @Test
  void a_first_grade_is_recorded_and_shown_next_to_its_student() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var student = studentOf(promotion);
    var exam = examOn(SemesterRef.S1);

    var saved =
        post(
            "/ui/admin/exams/" + exam.getId() + "/grades",
            entry(student, promotion, "14", "CREATION", "Final exam marked"),
            admin);

    assertEquals(302, saved.statusCode(), "body was " + saved.body());
    assertEquals(
        0,
        gradeRepository
            .findByExamIdAndStudentId(exam.getId(), student.getId())
            .orElseThrow()
            .getValue()
            .compareTo(new BigDecimal("14")));
    var page =
        get("/ui/admin/exams/" + exam.getId() + "/grades?promotion_id=" + promotion.getId(), admin);
    assertTrue(page.body().contains(student.getRef()), "the student should be listed");
    assertTrue(
        page.body().contains("Final exam marked"), "the reason should appear in the history");
  }

  @Test
  void a_correction_leaves_both_states_in_the_history() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var student = studentOf(promotion);
    var exam = examOn(SemesterRef.S1);
    post(
        "/ui/admin/exams/" + exam.getId() + "/grades",
        entry(student, promotion, "12", "CREATION", "Initial marking"),
        admin);

    var corrected =
        post(
            "/ui/admin/exams/" + exam.getId() + "/grades",
            entry(student, promotion, "15", "CLAIM", "Claim accepted after review"),
            admin);

    assertEquals(302, corrected.statusCode(), "body was " + corrected.body());
    var grade =
        gradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId()).orElseThrow();
    assertEquals(
        2, gradeHistoryRepository.findAllByGradeIdOrderByChangedAtDesc(grade.getId()).size());

    var page =
        get("/ui/admin/exams/" + exam.getId() + "/grades?promotion_id=" + promotion.getId(), admin);
    assertTrue(page.body().contains("Initial marking"), "the first entry must remain readable");
    assertTrue(page.body().contains("Claim accepted after review"));
    assertTrue(page.body().contains("CLAIM"));
  }

  @Test
  void resubmitting_the_same_value_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var student = studentOf(promotion);
    var exam = examOn(SemesterRef.S1);
    post(
        "/ui/admin/exams/" + exam.getId() + "/grades",
        entry(student, promotion, "12", "CREATION", "Initial marking"),
        admin);

    var again =
        post(
            "/ui/admin/exams/" + exam.getId() + "/grades",
            entry(student, promotion, "12", "CORRECTION", "Same value on purpose"),
            admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("equals the current value"), "body was " + again.body());
    assertTrue(!again.body().startsWith("{"), "the failure must not answer JSON");
  }

  @Test
  void a_first_entry_that_claims_to_be_a_correction_is_refused() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var student = studentOf(promotion);
    var exam = examOn(SemesterRef.S1);

    var response =
        post(
            "/ui/admin/exams/" + exam.getId() + "/grades",
            entry(student, promotion, "14", "CORRECTION", "There is nothing to correct yet"),
            admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("must use reason_type CREATION"), response.body());
  }

  @Test
  void a_grade_out_of_range_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var student = studentOf(promotion);
    var exam = examOn(SemesterRef.S1);

    var response =
        post(
            "/ui/admin/exams/" + exam.getId() + "/grades",
            entry(student, promotion, "21", "CREATION", "Above the scale"),
            admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("between 0 and 20"), response.body());
  }

  @Test
  void grading_a_student_who_has_chosen_no_track_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var student = studentOf(promotion);
    var exam = examOn(SemesterRef.S4);

    var response =
        post(
            "/ui/admin/exams/" + exam.getId() + "/grades",
            entry(student, promotion, "14", "CREATION", "Marked without a track"),
            admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("TRACK_NOT_SELECTED"), "body was " + response.body());
    assertTrue(
        gradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId()).isEmpty(),
        "nothing must have been recorded");
  }

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    var exam = examOn(SemesterRef.S1);

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          get("/ui/admin/exams/" + exam.getId() + "/grades", tokenFor(role)).statusCode(),
          "role " + role);
    }
  }
}
