package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.event.EventProducer;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.GradeRepository;
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
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentGroupAssignment;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class TeacherScopeIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired ExamRepository examRepository;
  @Autowired GradeRepository gradeRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired StudentGroupAssignmentRepository studentGroupAssignmentRepository;
  @Autowired TeachingAssignmentRepository teachingAssignmentRepository;
  @Autowired JwtService jwtService;

  @MockBean BucketComponent bucketComponent;
  @MockBean EventProducer<?> eventProducer;

  private static final Instant EXAM_DATE = Instant.parse("2026-01-15T00:00:00Z");

  private Promotion promotion;
  private Group taughtGroup;
  private Group otherGroup;
  private Student taughtStudent;
  private Student otherStudent;
  private Teacher teacher;
  private Teacher otherTeacher;
  private Course taughtCourse;
  private Course otherCourse;
  private String teacherToken;
  private String adminToken;
  private String taughtStudentToken;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser userOf(Role role) {
    return appUserRepository.save(
        AppUser.builder().email(rand(12) + "@hei.test").passwordHash("hash").role(role).build());
  }

  private Student studentIn(Group group) {
    var student =
        studentRepository.save(
            Student.builder()
                .ref(rand(18))
                .firstName("Jean")
                .lastName("Rakoto")
                .email(rand(12) + "@hei.test")
                .entranceDate(LocalDate.of(2025, 9, 1))
                .promotion(promotion)
                .user(userOf(Role.STUDENT))
                .build());
    studentGroupAssignmentRepository.save(
        StudentGroupAssignment.builder()
            .student(student)
            .group(group)
            .startDate(LocalDate.of(2025, 1, 1))
            .reason("Enrolled")
            .build());
    return student;
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

  private Exam examOf(Course course) {
    return examRepository.save(
        Exam.builder()
            .course(course)
            .title("Final exam")
            .dateExam(EXAM_DATE)
            .coefficient(BigDecimal.ONE)
            .build());
  }

  private Grade gradeOf(Student student, Exam exam) {
    return gradeRepository.save(
        Grade.builder()
            .student(student)
            .exam(exam)
            .value(new BigDecimal("14.00"))
            .updatedAt(Instant.now())
            .build());
  }

  private Teacher teacherAccount() {
    return teacherRepository.save(
        Teacher.builder()
            .ref(rand(18))
            .firstName("Aina")
            .lastName("Randria")
            .email(rand(12) + "@hei.test")
            .user(userOf(Role.TEACHER))
            .build());
  }

  @BeforeEach
  void school() {
    promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    taughtGroup = groupRepository.save(Group.builder().ref(rand(8)).promotion(promotion).build());
    otherGroup = groupRepository.save(Group.builder().ref(rand(8)).promotion(promotion).build());

    taughtStudent = studentIn(taughtGroup);
    otherStudent = studentIn(otherGroup);

    teacher = teacherAccount();
    otherTeacher = teacherAccount();

    taughtCourse = course();
    otherCourse = course();
    teachingAssignmentRepository.save(
        TeachingAssignment.builder()
            .course(taughtCourse)
            .teacher(teacher)
            .group(taughtGroup)
            .build());

    var taughtExam = examOf(taughtCourse);
    var otherExam = examOf(otherCourse);
    gradeOf(taughtStudent, taughtExam);
    gradeOf(taughtStudent, otherExam);
    gradeOf(otherStudent, taughtExam);

    teacherToken = jwtService.issue(teacher.getUser()).token();
    adminToken = jwtService.issue(userOf(Role.ADMIN)).token();
    taughtStudentToken = jwtService.issue(taughtStudent.getUser()).token();
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

  private String bodyOf(String path, String token) {
    return restTemplate
        .exchange(path, GET, new HttpEntity<>(bearer(token)), String.class)
        .getBody();
  }

  @Test
  void a_teacher_reads_the_record_of_a_student_they_teach() {
    assertEquals(200, statusOf(GET, "/students/" + taughtStudent.getId(), teacherToken));
  }

  @Test
  void a_teacher_is_refused_the_record_of_a_student_they_do_not_teach() {
    assertEquals(403, statusOf(GET, "/students/" + otherStudent.getId(), teacherToken));
  }

  @Test
  void a_teacher_is_refused_the_result_of_a_student_they_do_not_teach() {
    assertEquals(403, statusOf(GET, "/students/" + otherStudent.getId() + "/result", teacherToken));
  }

  @Test
  void a_teacher_is_refused_the_courses_of_a_student_they_do_not_teach() {
    assertEquals(
        403, statusOf(GET, "/students/" + otherStudent.getId() + "/courses", teacherToken));
  }

  @Test
  void a_teacher_is_refused_the_track_choices_of_a_student_they_do_not_teach() {
    assertEquals(
        403, statusOf(GET, "/students/" + otherStudent.getId() + "/track-choices", teacherToken));
  }

  @Test
  void a_teacher_is_refused_the_group_history_of_a_student_they_do_not_teach() {
    assertEquals(
        403,
        statusOf(GET, "/students/" + otherStudent.getId() + "/group-assignments", teacherToken));
  }

  @Test
  void a_teacher_sees_only_the_grades_of_the_courses_they_teach() {
    var body = bodyOf("/students/" + taughtStudent.getId() + "/grades", teacherToken);

    assertTrue(body.contains(taughtCourse.getRef()), "body was " + body);
    assertTrue(!body.contains(otherCourse.getRef()), "body was " + body);
  }

  @Test
  void a_teacher_sees_no_grade_of_a_student_they_do_not_teach() {
    var body = bodyOf("/students/" + otherStudent.getId() + "/grades", teacherToken);

    assertTrue(!body.contains(taughtCourse.getRef()), "body was " + body);
  }

  @Test
  void an_admin_still_sees_every_grade_of_any_student() {
    var body = bodyOf("/students/" + taughtStudent.getId() + "/grades", adminToken);

    assertTrue(body.contains(taughtCourse.getRef()), "body was " + body);
    assertTrue(body.contains(otherCourse.getRef()), "body was " + body);
  }

  @Test
  void a_student_still_sees_every_grade_of_their_own() {
    var body = bodyOf("/students/" + taughtStudent.getId() + "/grades", taughtStudentToken);

    assertTrue(body.contains(taughtCourse.getRef()), "body was " + body);
    assertTrue(body.contains(otherCourse.getRef()), "body was " + body);
  }

  @Test
  void a_teacher_cannot_order_the_transcript_of_a_student_they_do_not_teach() {
    assertEquals(
        403,
        statusOf(POST, "/students/" + otherStudent.getId() + "/transcript-requests", teacherToken));
  }

  @Test
  void a_teacher_cannot_order_the_transcript_of_a_student_they_do_teach() {
    assertEquals(
        403,
        statusOf(
            POST, "/students/" + taughtStudent.getId() + "/transcript-requests", teacherToken));
  }

  @Test
  void a_teacher_cannot_list_the_transcripts_of_a_student_they_do_teach() {
    assertEquals(
        403,
        statusOf(GET, "/students/" + taughtStudent.getId() + "/transcript-requests", teacherToken));
  }

  @Test
  void a_student_still_orders_their_own_transcript() {
    assertEquals(
        202,
        statusOf(
            POST,
            "/students/" + taughtStudent.getId() + "/transcript-requests",
            taughtStudentToken),
        "a student must keep the transcript workflow");
  }

  @Test
  void a_teacher_is_refused_the_record_of_another_teacher() {
    assertEquals(403, statusOf(GET, "/teachers/" + otherTeacher.getId(), teacherToken));
  }

  @Test
  void a_teacher_reads_their_own_record() {
    assertEquals(200, statusOf(GET, "/teachers/" + teacher.getId(), teacherToken));
  }

  @Test
  void a_student_is_refused_the_record_of_a_teacher() {
    assertEquals(403, statusOf(GET, "/teachers/" + teacher.getId(), taughtStudentToken));
  }

  @Test
  void an_admin_reads_any_record() {
    assertEquals(200, statusOf(GET, "/students/" + otherStudent.getId(), adminToken));
    assertEquals(200, statusOf(GET, "/teachers/" + teacher.getId(), adminToken));
  }

  @Test
  void a_student_is_still_refused_the_record_of_another_student() {
    assertEquals(403, statusOf(GET, "/students/" + otherStudent.getId(), taughtStudentToken));
  }
}
