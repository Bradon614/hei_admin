package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.Grade;
import com.exam.hei.endpoint.rest.model.GradeChange;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentGroupAssignment;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import com.exam.hei.service.StudentGroupAssignmentService;
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

/**
 * A grade is attached to a student and an exam, never to a group: the structural guarantee that a
 * later group change cannot make it disappear, nor make it visible to the wrong teacher.
 */
class GradesSurviveGroupChangeIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired ExamRepository examRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired TeachingAssignmentRepository teachingAssignmentRepository;
  @Autowired StudentGroupAssignmentRepository studentGroupAssignmentRepository;
  @Autowired StudentGroupAssignmentService studentGroupAssignmentService;
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

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    headers.set(AUTHORIZATION, "Bearer " + token);
    return headers;
  }

  @Test
  void
      a_grade_survives_a_group_change_and_stays_visible_only_to_the_teacher_of_the_group_it_was_earned_in() {
    var admin = tokenFor(Role.ADMIN);
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    var course =
        courseRepository.save(
            Course.builder()
                .ref(rand(20))
                .title("Course under test")
                .credits(6)
                .semester(semesterRepository.findByRef(SemesterRef.S1).orElseThrow())
                .build());
    var exam =
        examRepository.save(
            Exam.builder()
                .course(course)
                .title("Final")
                .dateExam(Instant.parse("2026-01-15T08:00:00Z"))
                .coefficient(new BigDecimal("2.00"))
                .build());

    var groupA = groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
    var groupB = groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
    var teacherOfA = teacherAccount();
    var teacherOfB = teacherAccount();
    teachingAssignmentRepository.save(
        TeachingAssignment.builder()
            .course(course)
            .teacher(teacherOfA.teacher())
            .group(groupA)
            .build());
    teachingAssignmentRepository.save(
        TeachingAssignment.builder()
            .course(course)
            .teacher(teacherOfB.teacher())
            .group(groupB)
            .build());

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

    // The student sat the exam while in group A.
    studentGroupAssignmentRepository.save(
        StudentGroupAssignment.builder()
            .student(student)
            .group(groupA)
            .startDate(LocalDate.of(2025, 9, 1))
            .build());

    var created =
        restTemplate
            .exchange(
                "/exams/" + exam.getId() + "/grades",
                PUT,
                new HttpEntity<>(
                    List.of(
                        GradeChange.builder()
                            .studentId(student.getId())
                            .value(new BigDecimal("14.00"))
                            .reasonType(GradeChangeReasonType.CREATION)
                            .reason("Initial entry")
                            .build()),
                    bearer(admin)),
                new ParameterizedTypeReference<List<Grade>>() {})
            .getBody()
            .get(0);

    // Only later does the student move to group B, well after the exam took place.
    studentGroupAssignmentService.changeGroup(
        student.getId(), groupB.getId(), LocalDate.of(2026, 2, 1), "Reorganization");

    // The grade itself is untouched by the move.
    var stillThere =
        restTemplate.exchange(
            "/grades/" + created.getId(), GET, new HttpEntity<>(bearer(admin)), Grade.class);
    assertEquals(OK, stillThere.getStatusCode());
    assertEquals(0, new BigDecimal("14.00").compareTo(stillThere.getBody().getValue()));

    // The teacher who covered group A, where the student sat the exam, still sees the grade.
    var seenByTeacherOfA =
        restTemplate.exchange(
            "/exams/" + exam.getId() + "/grades",
            GET,
            new HttpEntity<>(bearer(teacherOfA.token())),
            new ParameterizedTypeReference<List<Grade>>() {});
    assertEquals(1, seenByTeacherOfA.getBody().size());

    // The teacher of group B, the student's group today, never covered them at exam time and does
    // not see the grade: resolution goes by the exam date, never by the current group.
    var seenByTeacherOfB =
        restTemplate.exchange(
            "/exams/" + exam.getId() + "/grades",
            GET,
            new HttpEntity<>(bearer(teacherOfB.token())),
            new ParameterizedTypeReference<List<Grade>>() {});
    assertEquals(0, seenByTeacherOfB.getBody().size());
  }
}
