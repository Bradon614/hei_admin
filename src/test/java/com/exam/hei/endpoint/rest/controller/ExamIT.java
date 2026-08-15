package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.Exam;
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
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class ExamIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TeachingAssignmentRepository teachingAssignmentRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String keyOf(Role role) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(role)
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

  private Course course() {
    return courseRepository.save(
        Course.builder()
            .ref(rand(20))
            .title("Course under test")
            .credits(6)
            .semester(semesterRepository.findByRef(SemesterRef.S1).orElseThrow())
            .build());
  }

  private record TeacherAccount(Teacher teacher, String token) {}

  private TeacherAccount teacherAccount() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.TEACHER)
                .build());
    var teacher =
        teacherRepository.save(
            Teacher.builder()
                .ref(rand(20))
                .firstName("Aina")
                .lastName("Randria")
                .email(rand(12) + "@hei.test")
                .user(user)
                .build());
    return new TeacherAccount(teacher, jwtService.issue(user).token());
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
    return groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
  }

  private void assign(Course course, Teacher teacher, Group group) {
    teachingAssignmentRepository.save(
        TeachingAssignment.builder().course(course).teacher(teacher).group(group).build());
  }

  private static Exam anExam(String title, String coefficient, String date) {
    return Exam.builder()
        .title(title)
        .coefficient(new BigDecimal(coefficient))
        .dateExam(Instant.parse(date))
        .build();
  }

  private ResponseEntity<List<Exam>> put(Course course, List<Exam> body, String apiKey) {
    return restTemplate.exchange(
        "/courses/" + course.getId() + "/exams",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> putRaw(Course course, List<Exam> body, String apiKey) {
    return restTemplate.exchange(
        "/courses/" + course.getId() + "/exams",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        String.class);
  }

  @Test
  void listing_exams_requires_authentication() {
    assertEquals(
        UNAUTHORIZED,
        restTemplate
            .exchange(
                "/courses/" + course().getId() + "/exams",
                GET,
                new HttpEntity<>(bearer(null)),
                String.class)
            .getStatusCode());
  }

  @Test
  void exams_are_created_then_listed_by_date() {
    var admin = keyOf(Role.ADMIN);
    var course = course();

    put(
        course,
        List.of(
            anExam("Final", "2.00", "2026-01-15T08:00:00Z"),
            anExam("Midterm", "1.00", "2025-11-20T08:00:00Z")),
        admin);

    var listed =
        restTemplate.exchange(
            "/courses/" + course.getId() + "/exams",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Exam>>() {});

    assertEquals(OK, listed.getStatusCode());
    assertEquals(
        List.of("Midterm", "Final"), listed.getBody().stream().map(Exam::getTitle).toList());
    assertNotNull(listed.getBody().get(0).getId());
  }

  @Test
  void an_exam_carries_its_weight_and_its_date() {
    var course = course();

    var created =
        put(course, List.of(anExam("Final", "2.50", "2026-01-15T08:00:00Z")), keyOf(Role.ADMIN))
            .getBody()
            .get(0);

    assertEquals(0, new BigDecimal("2.50").compareTo(created.getCoefficient()));
    assertEquals(Instant.parse("2026-01-15T08:00:00Z"), created.getDateExam());
    assertEquals(course.getId(), created.getCourse().getId());
  }

  @Test
  void a_weightless_exam_is_refused() {
    var response =
        putRaw(
            course(),
            List.of(anExam("Weightless", "0.00", "2026-01-15T08:00:00Z")),
            keyOf(Role.ADMIN));

    assertEquals(400, response.getStatusCode().value());
    assertTrue(response.getBody().contains("coefficient"), "body was " + response.getBody());
  }

  @Test
  void exams_of_an_unknown_course_are_not_found() {
    assertEquals(
        NOT_FOUND,
        restTemplate
            .exchange(
                "/courses/" + UUID.randomUUID() + "/exams",
                GET,
                new HttpEntity<>(bearer(keyOf(Role.ADMIN))),
                String.class)
            .getStatusCode());
  }

  @Test
  void a_student_cannot_write_exams() {
    assertEquals(
        403,
        putRaw(
                course(),
                List.of(anExam("Final", "2.00", "2026-01-15T08:00:00Z")),
                keyOf(Role.STUDENT))
            .getStatusCode()
            .value());
  }

  @Test
  void an_assigned_teacher_can_write_the_exams_of_their_course() {
    var course = course();
    var teacher = teacherAccount();
    assign(course, teacher.teacher(), group());

    var response =
        put(course, List.of(anExam("Final", "2.00", "2026-01-15T08:00:00Z")), teacher.token());

    assertEquals(OK, response.getStatusCode());
    assertEquals("Final", response.getBody().get(0).getTitle());
  }

  @Test
  void an_unassigned_teacher_cannot_write_the_exams_of_a_course() {
    // No teaching assignment names this teacher for that course: the specification allows an
    // assigned teacher to write exams, not every teacher.
    var course = course();
    var teacher = teacherAccount();

    assertEquals(
        403,
        putRaw(course, List.of(anExam("Final", "2.00", "2026-01-15T08:00:00Z")), teacher.token())
            .getStatusCode()
            .value());
  }

  @Test
  void exams_are_serialized_in_snake_case() {
    var admin = keyOf(Role.ADMIN);
    var course = course();
    put(course, List.of(anExam("Final", "2.00", "2026-01-15T08:00:00Z")), admin);

    var raw =
        restTemplate
            .exchange(
                "/courses/" + course.getId() + "/exams",
                GET,
                new HttpEntity<>(bearer(admin)),
                String.class)
            .getBody();

    assertTrue(raw.contains("\"date_exam\""), "body was " + raw);
  }
}
