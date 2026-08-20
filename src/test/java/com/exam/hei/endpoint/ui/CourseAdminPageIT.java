package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class CourseAdminPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired ExamRepository examRepository;
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

  private Map<String, String> newCourse(String ref, String semesterRef) {
    var fields = new HashMap<String, String>();
    fields.put("ref", ref);
    fields.put("title", "Course under test");
    fields.put("credits", "6");
    fields.put("semester_ref", semesterRef);
    return fields;
  }

  @Test
  void a_course_created_from_the_form_reaches_the_catalogue() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = rand(18);

    var created = post("/ui/admin/courses", newCourse(ref, "S6"), admin);

    assertEquals(302, created.statusCode(), "body was " + created.body());
    assertTrue(courseRepository.findByRef(ref).isPresent(), "the course was not persisted");
    assertTrue(
        get("/ui/admin/courses?semester_ref=S6", admin).body().contains(ref),
        "the listing did not show the new course");
  }

  @Test
  void the_semester_filter_leaves_out_the_other_semesters() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = rand(18);
    post("/ui/admin/courses", newCourse(ref, "S6"), admin);

    var page = get("/ui/admin/courses?semester_ref=S2", admin);

    assertTrue(!page.body().contains(ref), "an S6 course must not surface under S2");
  }

  @Test
  void a_track_course_is_refused_on_a_common_core_semester() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var fields = newCourse(rand(18), "S1");
    fields.put("track_id", trackRepository.findAll().get(0).getId().toString());

    var response = post("/ui/admin/courses", fields, admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("common core"), "body was " + response.body());
    assertTrue(response.body().contains("<form"), "the form must come back");
  }

  @Test
  void a_track_course_is_accepted_from_the_fourth_semester() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = rand(18);
    var fields = newCourse(ref, "S4");
    fields.put("track_id", trackRepository.findAll().get(0).getId().toString());

    var response = post("/ui/admin/courses", fields, admin);

    assertEquals(302, response.statusCode(), "body was " + response.body());
    assertTrue(courseRepository.findByRef(ref).orElseThrow().getTrack() != null);
  }

  @Test
  void a_duplicate_reference_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = rand(18);
    post("/ui/admin/courses", newCourse(ref, "S1"), admin);

    var again = post("/ui/admin/courses", newCourse(ref, "S1"), admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("<form"), "the form must come back");
    assertTrue(!again.body().startsWith("{"), "body was " + again.body());
  }

  @Test
  void an_exam_added_to_a_course_shows_up_under_it() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = rand(18);
    post("/ui/admin/courses", newCourse(ref, "S1"), admin);
    var course = courseRepository.findByRef(ref).orElseThrow();

    var created =
        post(
            "/ui/admin/courses/" + course.getId() + "/exams",
            Map.of("title", "Final exam", "date_exam", "2026-01-15", "coefficient", "2"),
            admin);

    assertEquals(302, created.statusCode(), "body was " + created.body());
    assertEquals(1, examRepository.findAllByCourseIdOrderByDateExamAsc(course.getId()).size());
    var page = get("/ui/admin/courses/" + course.getId() + "/exams", admin);
    assertTrue(page.body().contains("Final exam"));
    assertTrue(page.body().contains("2026-01-15"), "the exam date should be readable as a day");
  }

  @Test
  void an_exam_that_weighs_nothing_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = rand(18);
    post("/ui/admin/courses", newCourse(ref, "S1"), admin);
    var course = courseRepository.findByRef(ref).orElseThrow();

    var response =
        post(
            "/ui/admin/courses/" + course.getId() + "/exams",
            Map.of("title", "Weightless", "date_exam", "2026-01-15", "coefficient", "0"),
            admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("coefficient greater than zero"), response.body());
    assertTrue(examRepository.findAllByCourseIdOrderByDateExamAsc(course.getId()).isEmpty());
  }

  @Test
  void an_unknown_course_is_reported_rather_than_thrown() throws Exception {
    var admin = tokenFor(Role.ADMIN);

    var response = get("/ui/admin/courses/" + UUID.randomUUID() + "/exams", admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("not found"), "body was " + response.body());
  }

  @Test
  void the_screens_are_closed_to_the_other_roles() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      var token = tokenFor(role);
      assertEquals(403, get("/ui/admin/courses", token).statusCode(), "role " + role);
      assertEquals(
          403,
          get("/ui/admin/courses/" + UUID.randomUUID() + "/exams", token).statusCode(),
          "role " + role);
    }
  }
}
