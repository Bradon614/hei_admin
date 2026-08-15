package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.PUT;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.GradeChange;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

/** The Thymeleaf page, driven the way a browser drives it: with a cookie, never a header. */
class GraduatePageIT extends FacadeIT {

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

  private Student student(Promotion promotion, String lastName, String firstName) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName(firstName)
            .lastName(lastName)
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(user)
            .build());
  }

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

  private void graduate(Student student, String average, String adminToken) {
    studentTrackChoiceRepository.save(
        StudentTrackChoice.builder()
            .student(student)
            .track(trackRepository.findByCode("EL").orElseThrow())
            .fromSemester(semesterRepository.findByRef(SemesterRef.S4).orElseThrow())
            .build());
    for (var ref : SemesterRef.values()) {
      var headers = new HttpHeaders();
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
      restTemplate.exchange(
          "/exams/" + soleExamOf(ref).getId() + "/grades",
          PUT,
          new HttpEntity<>(
              List.of(
                  GradeChange.builder()
                      .studentId(student.getId())
                      .value(new BigDecimal(average))
                      .reasonType(GradeChangeReasonType.CREATION)
                      .reason("Initial entry")
                      .build()),
              headers),
          String.class);
    }
  }

  private HttpResponse<String> page(String query, String cookieToken) throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/ui/graduates" + query));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void a_signed_out_visitor_is_sent_to_the_sign_in_page() throws Exception {
    var response = page("", null);

    assertEquals(302, response.statusCode());
    assertTrue(
        response.headers().firstValue("Location").orElseThrow().endsWith("/ui/login"),
        "Location was " + response.headers().firstValue("Location"));
  }

  @Test
  void a_non_admin_is_refused() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(403, page("", tokenFor(role)).statusCode(), "role " + role);
    }
  }

  @Test
  void an_admin_without_a_chosen_promotion_gets_the_picker_alone() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    promotion();

    var response = page("", admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<select"), "the promotion picker must be rendered");
    assertFalse(response.body().contains("<table"), "no table until a promotion is chosen");
  }

  @Test
  void the_page_lists_the_graduates_of_the_chosen_promotion() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    graduate(student(promotion, "Rakoto", "Jean"), "14.00", admin);

    var body = page("?promotion_id=" + promotion.getId(), admin).body();

    assertTrue(body.contains("Rakoto"), "body was " + body);
    assertTrue(body.contains("Jean"), "body was " + body);
    assertTrue(body.contains("<table"), "the graduate table must be rendered");
  }

  @Test
  void the_download_button_points_at_the_excel_endpoint() throws Exception {
    // The button is a plain link rather than a script: the SameSite=Strict cookie rides along on a
    // same-site navigation, which is exactly what makes cookie authentication worth its cost here.
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    graduate(student(promotion, "Rakoto", "Jean"), "14.00", admin);

    var body = page("?promotion_id=" + promotion.getId(), admin).body();

    assertTrue(
        body.contains("/promotions/" + promotion.getId() + "/graduates/excel"), "body was " + body);
  }

  @Test
  void a_promotion_with_no_graduate_says_so_rather_than_showing_an_empty_table() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    student(promotion, "Rakoto", "Jean");

    var body = page("?promotion_id=" + promotion.getId(), admin).body();

    assertTrue(body.contains("has graduated yet"), "body was " + body);
    assertFalse(body.contains("<table"), "an empty table would read as a rendering bug");
  }

  @Test
  void an_unknown_promotion_is_not_found() throws Exception {
    assertEquals(
        404, page("?promotion_id=" + UUID.randomUUID(), tokenFor(Role.ADMIN)).statusCode());
  }
}
