package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Teacher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountLifecycleIT extends FacadeIT {

  private static final String PASSWORD = "account-demo-2025";

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser userOf(Role role) {
    return appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .role(role)
            .build());
  }

  private Student student() {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(18))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(userOf(Role.STUDENT))
            .build());
  }

  private Teacher teacher() {
    return teacherRepository.save(
        Teacher.builder()
            .ref(rand(18))
            .firstName("Aina")
            .lastName("Randria")
            .email(rand(12) + "@hei.test")
            .user(userOf(Role.TEACHER))
            .build());
  }

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private int statusOf(HttpMethod method, String path, String token) {
    return restTemplate
        .exchange(path, method, new HttpEntity<>("{}", bearer(token)), String.class)
        .getStatusCode()
        .value();
  }

  private int loginStatus(String email) throws Exception {
    var body = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  private String adminToken() {
    return jwtService.issue(userOf(Role.ADMIN)).token();
  }

  @Test
  void an_admin_deactivates_a_student_account() {
    var student = student();

    var status = statusOf(POST, "/students/" + student.getId() + "/deactivation", adminToken());

    assertEquals(200, status);
    assertFalse(appUserRepository.findById(student.getUser().getId()).orElseThrow().isEnabled());
  }

  @Test
  void a_deactivated_account_can_no_longer_sign_in() throws Exception {
    var student = student();
    var email = student.getUser().getEmail();
    assertEquals(200, loginStatus(email));

    statusOf(POST, "/students/" + student.getId() + "/deactivation", adminToken());

    assertEquals(401, loginStatus(email));
  }

  @Test
  void a_token_issued_before_the_deactivation_stops_working() {
    var student = student();
    var alreadyIssued = jwtService.issue(student.getUser()).token();
    assertEquals(200, statusOf(GET, "/whoami", alreadyIssued));

    statusOf(POST, "/students/" + student.getId() + "/deactivation", adminToken());

    assertEquals(401, statusOf(GET, "/whoami", alreadyIssued));
  }

  @Test
  void a_deactivated_account_reads_nothing_at_all() {
    var student = student();
    var alreadyIssued = jwtService.issue(student.getUser()).token();
    statusOf(POST, "/students/" + student.getId() + "/deactivation", adminToken());

    assertEquals(401, statusOf(GET, "/students/" + student.getId(), alreadyIssued));
    assertEquals(401, statusOf(GET, "/courses", alreadyIssued));
  }

  @Test
  void an_admin_reactivates_an_account() throws Exception {
    var student = student();
    var email = student.getUser().getEmail();
    var admin = adminToken();
    statusOf(POST, "/students/" + student.getId() + "/deactivation", admin);
    assertEquals(401, loginStatus(email));

    var status = statusOf(POST, "/students/" + student.getId() + "/activation", admin);

    assertEquals(200, status);
    assertEquals(200, loginStatus(email));
  }

  @Test
  void an_admin_deactivates_a_teacher_account() {
    var teacher = teacher();

    var status = statusOf(POST, "/teachers/" + teacher.getId() + "/deactivation", adminToken());

    assertEquals(200, status);
    assertFalse(appUserRepository.findById(teacher.getUser().getId()).orElseThrow().isEnabled());
  }

  @Test
  void a_teacher_cannot_deactivate_an_account() {
    var student = student();
    var teacherToken = jwtService.issue(teacher().getUser()).token();

    assertEquals(
        403, statusOf(POST, "/students/" + student.getId() + "/deactivation", teacherToken));
    assertTrue(appUserRepository.findById(student.getUser().getId()).orElseThrow().isEnabled());
  }

  @Test
  void a_student_cannot_deactivate_an_account() {
    var student = student();
    var other = student();
    var studentToken = jwtService.issue(student.getUser()).token();

    assertEquals(403, statusOf(POST, "/students/" + other.getId() + "/deactivation", studentToken));
    assertTrue(appUserRepository.findById(other.getUser().getId()).orElseThrow().isEnabled());
  }

  @Test
  void a_student_cannot_deactivate_their_own_account() {
    var student = student();
    var studentToken = jwtService.issue(student.getUser()).token();

    assertEquals(
        403, statusOf(POST, "/students/" + student.getId() + "/deactivation", studentToken));
  }

  @Test
  void an_unknown_profile_is_not_found() {
    assertEquals(
        404, statusOf(POST, "/students/" + UUID.randomUUID() + "/deactivation", adminToken()));
    assertEquals(
        404, statusOf(POST, "/teachers/" + UUID.randomUUID() + "/deactivation", adminToken()));
  }

  @Test
  void an_anonymous_caller_cannot_deactivate_an_account() throws Exception {
    var student = student();
    var request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    restTemplate.getRootUri() + "/students/" + student.getId() + "/deactivation"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(401, response.statusCode());
  }
}
