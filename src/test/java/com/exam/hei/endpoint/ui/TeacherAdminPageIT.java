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
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class TeacherAdminPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired TeachingAssignmentRepository teachingAssignmentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

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

  private HttpResponse<String> login(String email, String password) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private Map<String, String> newTeacher(String email) {
    return Map.of(
        "ref", rand(18),
        "first_name", "Aina",
        "last_name", "Randria",
        "email", email,
        "password", "teacher-demo-2025");
  }

  private Group group() {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    return groupRepository.save(Group.builder().ref(rand(8)).promotion(promotion).build());
  }

  private Course course() {
    return courseRepository.save(
        Course.builder()
            .ref(rand(18))
            .title("Course under test")
            .credits(6)
            .semester(semesterRepository.findByRef(SemesterRef.S1).orElseThrow())
            .build());
  }

  // --- the demo scenario ---------------------------------------------------------------

  @Test
  void a_teacher_created_from_the_form_can_actually_sign_in() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";

    var created = post("/ui/admin/teachers", newTeacher(email), admin);
    assertEquals(302, created.statusCode(), "body was " + created.body());

    var signedIn = login(email, "teacher-demo-2025");

    assertEquals(200, signedIn.statusCode(), "body was " + signedIn.body());
    assertTrue(
        objectMapper.readTree(signedIn.body()).get("access_token").asText().split("\\.").length
            == 3);
  }

  @Test
  void the_created_account_carries_the_teacher_role() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";

    post("/ui/admin/teachers", newTeacher(email), admin);

    assertEquals(Role.TEACHER, appUserRepository.findByEmail(email).orElseThrow().getRole());
  }

  @Test
  void the_password_is_stored_hashed() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";

    post("/ui/admin/teachers", newTeacher(email), admin);

    var hash = appUserRepository.findByEmail(email).orElseThrow().getPasswordHash();
    assertTrue(hash.startsWith("$2"), "expected a BCrypt hash but was " + hash);
    assertTrue(!hash.contains("teacher-demo-2025"));
  }

  @Test
  void the_new_teacher_appears_in_the_faculty_listing() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/teachers", newTeacher(email), admin);

    var page = get("/ui/admin/teachers", admin);

    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains(email), "listing did not show the teacher");
  }

  // --- teaching assignments --------------------------------------------------------------

  @Test
  void a_course_can_be_assigned_to_a_teacher_for_a_group() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/teachers", newTeacher(email), admin);
    var teacher = teacherRepository.findByEmail(email).orElseThrow();
    var course = course();
    var group = group();

    var response =
        post(
            "/ui/admin/teachers/" + teacher.getId() + "/assignments",
            Map.of("course_id", course.getId().toString(), "group_id", group.getId().toString()),
            admin);

    assertEquals(302, response.statusCode(), "body was " + response.body());
    assertTrue(
        teachingAssignmentRepository.existsByCourseIdAndTeacherIdAndGroupId(
            course.getId(), teacher.getId(), group.getId()));
  }

  @Test
  void the_assignment_shows_up_next_to_its_teacher() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/teachers", newTeacher(email), admin);
    var teacher = teacherRepository.findByEmail(email).orElseThrow();
    var course = course();
    var group = group();
    post(
        "/ui/admin/teachers/" + teacher.getId() + "/assignments",
        Map.of("course_id", course.getId().toString(), "group_id", group.getId().toString()),
        admin);

    var page = get("/ui/admin/teachers", admin);

    assertTrue(
        page.body().contains(course.getRef() + " · " + group.getRef()),
        "the assignment was not listed");
  }

  @Test
  void assigning_the_same_triple_twice_is_reported_on_the_form() throws Exception {
    // UNIQUE (course_id, teacher_id, group_id): the second attempt must come back as a message,
    // not as a stack trace or a JSON payload.
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/teachers", newTeacher(email), admin);
    var teacher = teacherRepository.findByEmail(email).orElseThrow();
    var fields =
        Map.of(
            "course_id", course().getId().toString(),
            "group_id", group().getId().toString());
    post("/ui/admin/teachers/" + teacher.getId() + "/assignments", fields, admin);

    var again = post("/ui/admin/teachers/" + teacher.getId() + "/assignments", fields, admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("<form"), "the page must come back");
    assertTrue(!again.body().startsWith("{"), "body was " + again.body());
  }

  // --- failures and access ------------------------------------------------------------------

  @Test
  void a_duplicate_email_re_renders_the_form_instead_of_answering_json() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/teachers", newTeacher(email), admin);

    var again = post("/ui/admin/teachers", newTeacher(email), admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("<form"));
    assertTrue(!again.body().startsWith("{"), "body was " + again.body());
  }

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(403, get("/ui/admin/teachers", tokenFor(role)).statusCode(), "role " + role);
    }
  }
}
