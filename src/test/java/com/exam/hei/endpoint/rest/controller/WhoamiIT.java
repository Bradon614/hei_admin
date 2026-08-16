package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.Whoami;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Teacher;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class WhoamiIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser persistedUser(Role role) {
    return appUserRepository.save(
        AppUser.builder().email(rand(12) + "@hei.test").passwordHash("hash").role(role).build());
  }

  private Student persistedStudent(AppUser account) {
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
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(account.getEmail())
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(account)
            .build());
  }

  private Teacher persistedTeacher(AppUser account) {
    return teacherRepository.save(
        Teacher.builder()
            .ref(rand(20))
            .firstName("Aina")
            .lastName("Randria")
            .email(account.getEmail())
            .user(account)
            .build());
  }

  private <T> ResponseEntity<T> whoamiAs(AppUser user, Class<T> responseType) {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + jwtService.issue(user).token());
    return restTemplate.exchange(
        "/whoami", HttpMethod.GET, new HttpEntity<>(headers), responseType);
  }

  @Test
  void whoami_returns_the_identity_of_the_calling_account() {
    var user = persistedUser(Role.ADMIN);

    var body = whoamiAs(user, Whoami.class).getBody();

    assertEquals(user.getId(), body.getUserId());
    assertEquals(user.getEmail(), body.getEmail());
    assertEquals(Role.ADMIN, body.getRole());
  }

  @Test
  void whoami_reports_the_role_of_each_account() {
    assertEquals(
        Role.STUDENT, whoamiAs(persistedUser(Role.STUDENT), Whoami.class).getBody().getRole());
    assertEquals(
        Role.TEACHER, whoamiAs(persistedUser(Role.TEACHER), Whoami.class).getBody().getRole());
  }

  @Test
  void whoami_links_a_student_account_to_its_record() {
    var account = persistedUser(Role.STUDENT);
    var student = persistedStudent(account);

    var body = whoamiAs(account, Whoami.class).getBody();

    assertEquals(student.getId(), body.getStudentId());
    assertNull(body.getTeacherId());
  }

  @Test
  void whoami_links_a_teacher_account_to_its_record() {
    var account = persistedUser(Role.TEACHER);
    var teacher = persistedTeacher(account);

    var body = whoamiAs(account, Whoami.class).getBody();

    assertEquals(teacher.getId(), body.getTeacherId());
    assertNull(body.getStudentId());
  }

  @Test
  void an_admin_is_linked_to_no_profile() {
    var body = whoamiAs(persistedUser(Role.ADMIN), Whoami.class).getBody();

    assertNull(body.getStudentId());
    assertNull(body.getTeacherId());
  }

  @Test
  void an_account_without_a_profile_is_linked_to_nothing() {
    var body = whoamiAs(persistedUser(Role.STUDENT), Whoami.class).getBody();

    assertNull(body.getStudentId());
    assertNull(body.getTeacherId());
  }

  @Test
  void whoami_is_serialized_in_snake_case() {
    var account = persistedUser(Role.STUDENT);
    persistedStudent(account);

    var raw = whoamiAs(account, String.class).getBody();

    assertTrue(raw.contains("\"user_id\""), "body was " + raw);
    assertTrue(raw.contains("\"student_id\""), "body was " + raw);
    assertFalse(raw.contains("\"userId\""), "body was " + raw);
  }

  @Test
  void whoami_never_exposes_the_credential() {
    var raw = whoamiAs(persistedUser(Role.ADMIN), String.class).getBody();

    assertFalse(raw.contains("api_key"), "body was " + raw);
    assertFalse(raw.contains("password"), "body was " + raw);
  }
}
