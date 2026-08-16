package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Teacher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class TeachingAssignmentIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired JwtService jwtService;

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private record TeacherAccount(Teacher teacher, String token) {}

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

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
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

  private static com.exam.hei.endpoint.rest.model.TeachingAssignment anAssignment(
      Course course, Teacher teacher, Group group) {
    return com.exam.hei.endpoint.rest.model.TeachingAssignment.builder()
        .course(com.exam.hei.endpoint.rest.model.Course.builder().id(course.getId()).build())
        .teacher(com.exam.hei.endpoint.rest.model.Teacher.builder().id(teacher.getId()).build())
        .group(com.exam.hei.endpoint.rest.model.Group.builder().id(group.getId()).build())
        .build();
  }

  private ResponseEntity<List<com.exam.hei.endpoint.rest.model.TeachingAssignment>> put(
      List<com.exam.hei.endpoint.rest.model.TeachingAssignment> body, String token) {
    return restTemplate.exchange(
        "/teaching-assignments",
        PUT,
        new HttpEntity<>(body, bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> putRaw(
      List<com.exam.hei.endpoint.rest.model.TeachingAssignment> body, String token) {
    return restTemplate.exchange(
        "/teaching-assignments", PUT, new HttpEntity<>(body, bearer(token)), String.class);
  }

  private ResponseEntity<List<com.exam.hei.endpoint.rest.model.TeachingAssignment>> getByTeacher(
      UUID teacherId, String token) {
    return restTemplate.exchange(
        "/teachers/" + teacherId + "/teaching-assignments",
        GET,
        new HttpEntity<>(bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> getByTeacherRaw(UUID teacherId, String token) {
    return restTemplate.exchange(
        "/teachers/" + teacherId + "/teaching-assignments",
        GET,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  @Test
  void writing_an_assignment_requires_authentication() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/teaching-assignments"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("[]"))
            .build();

    var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(401, response.statusCode());
  }

  @Test
  void an_assignment_binds_a_course_a_teacher_and_a_group() {
    var admin = tokenFor(Role.ADMIN);
    var course = course();
    var teacher = teacherAccount().teacher();
    var group = group();

    var created = put(List.of(anAssignment(course, teacher, group)), admin).getBody().get(0);

    assertEquals(course.getId(), created.getCourse().getId());
    assertEquals(teacher.getId(), created.getTeacher().getId());
    assertEquals(group.getId(), created.getGroup().getId());
  }

  @Test
  void only_an_admin_can_write_teaching_assignments() {
    var course = course();
    var teacher = teacherAccount().teacher();
    var group = group();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          putRaw(List.of(anAssignment(course, teacher, group)), tokenFor(role))
              .getStatusCode()
              .value(),
          "role " + role);
    }
  }

  @Test
  void the_same_course_teacher_group_triple_cannot_be_assigned_twice() {
    var admin = tokenFor(Role.ADMIN);
    var course = course();
    var teacher = teacherAccount().teacher();
    var group = group();
    put(List.of(anAssignment(course, teacher, group)), admin);

    assertEquals(
        CONFLICT, putRaw(List.of(anAssignment(course, teacher, group)), admin).getStatusCode());
  }

  @Test
  void an_assignment_naming_an_unknown_course_is_not_found() {
    var admin = tokenFor(Role.ADMIN);
    var unknown = Course.builder().id(UUID.randomUUID()).build();

    assertEquals(
        NOT_FOUND,
        putRaw(List.of(anAssignment(unknown, teacherAccount().teacher(), group())), admin)
            .getStatusCode());
  }

  @Test
  void a_teacher_reads_their_own_teaching_assignments() {
    var teacher = teacherAccount();
    var group = group();
    var admin = tokenFor(Role.ADMIN);
    put(List.of(anAssignment(course(), teacher.teacher(), group)), admin);

    var found = getByTeacher(teacher.teacher().getId(), teacher.token());

    assertEquals(OK, found.getStatusCode());
    assertEquals(1, found.getBody().size());
  }

  @Test
  void a_teacher_cannot_read_another_teacher_s_teaching_assignments() {
    var teacher = teacherAccount();
    var another = teacherAccount();

    assertEquals(
        403, getByTeacherRaw(teacher.teacher().getId(), another.token()).getStatusCode().value());
  }

  @Test
  void an_admin_reads_any_teacher_s_teaching_assignments() {
    var teacher = teacherAccount();

    assertEquals(OK, getByTeacher(teacher.teacher().getId(), tokenFor(Role.ADMIN)).getStatusCode());
  }

  @Test
  void teaching_assignments_are_serialized_in_snake_case() {
    var admin = tokenFor(Role.ADMIN);
    var teacher = teacherAccount().teacher();
    put(List.of(anAssignment(course(), teacher, group())), admin);

    var raw = getByTeacherRaw(teacher.getId(), admin).getBody();

    assertTrue(raw.contains("\"first_name\""), "body was " + raw);
  }
}
