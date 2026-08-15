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
import com.exam.hei.endpoint.rest.model.Grade;
import com.exam.hei.endpoint.rest.model.GradeChange;
import com.exam.hei.endpoint.rest.model.GradeHistory;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.StudentTrackChoiceRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.TrackRepository;
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
import com.exam.hei.repository.model.StudentTrackChoice;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class GradeIT extends FacadeIT {

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
  @Autowired StudentTrackChoiceRepository studentTrackChoiceRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired JwtService jwtService;

  /** See {@code AuthIT}: HttpURLConnection cannot process a 401 answer to a PUT carrying a body. */
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

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

  private record StudentAccount(Student student, String token) {}

  private StudentAccount studentAccount(Promotion promotion) {
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
    return new StudentAccount(student, jwtService.issue(user).token());
  }

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
    }
    return headers;
  }

  private Course course(SemesterRef semesterRef) {
    return courseRepository.save(
        Course.builder()
            .ref(rand(20))
            .title("Course under test")
            .credits(6)
            .semester(semesterRepository.findByRef(semesterRef).orElseThrow())
            .build());
  }

  private Exam exam(Course course, String date) {
    return examRepository.save(
        Exam.builder()
            .course(course)
            .title("Final")
            .dateExam(Instant.parse(date))
            .coefficient(new BigDecimal("2.00"))
            .build());
  }

  private Promotion promotion() {
    return promotionRepository.save(
        Promotion.builder()
            .ref(TestRefs.promotionRef())
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .tracks(List.of(trackRepository.findByCode("EL").orElseThrow()))
            .build());
  }

  private Group group(Promotion promotion) {
    return groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
  }

  private void assignTeaching(Course course, Teacher teacher, Group group) {
    teachingAssignmentRepository.save(
        TeachingAssignment.builder().course(course).teacher(teacher).group(group).build());
  }

  private void assignToGroup(Student student, Group group, String startDate) {
    studentGroupAssignmentRepository.save(
        StudentGroupAssignment.builder()
            .student(student)
            .group(group)
            .startDate(LocalDate.parse(startDate))
            .build());
  }

  private void chooseTrack(Student student) {
    studentTrackChoiceRepository.save(
        StudentTrackChoice.builder()
            .student(student)
            .track(trackRepository.findByCode("EL").orElseThrow())
            .fromSemester(semesterRepository.findByRef(SemesterRef.S4).orElseThrow())
            .build());
  }

  private static GradeChange creation(UUID studentId, String value) {
    return GradeChange.builder()
        .studentId(studentId)
        .value(new BigDecimal(value))
        .reasonType(GradeChangeReasonType.CREATION)
        .reason("Initial entry")
        .build();
  }

  private static GradeChange update(
      UUID studentId, String value, GradeChangeReasonType reasonType, String reason) {
    return GradeChange.builder()
        .studentId(studentId)
        .value(new BigDecimal(value))
        .reasonType(reasonType)
        .reason(reason)
        .build();
  }

  private ResponseEntity<List<Grade>> put(Exam exam, List<GradeChange> body, String token) {
    return restTemplate.exchange(
        "/exams/" + exam.getId() + "/grades",
        PUT,
        new HttpEntity<>(body, bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> putRaw(Exam exam, List<GradeChange> body, String token) {
    return restTemplate.exchange(
        "/exams/" + exam.getId() + "/grades",
        PUT,
        new HttpEntity<>(body, bearer(token)),
        String.class);
  }

  private ResponseEntity<List<Grade>> examGrades(Exam exam, String token) {
    return restTemplate.exchange(
        "/exams/" + exam.getId() + "/grades",
        GET,
        new HttpEntity<>(bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<List<Grade>> studentGrades(
      Student student, String semesterRef, String token) {
    var url =
        "/students/"
            + student.getId()
            + "/grades"
            + (semesterRef == null ? "" : "?semester_ref=" + semesterRef);
    return restTemplate.exchange(
        url, GET, new HttpEntity<>(bearer(token)), new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> studentGradesRaw(Student student, String token) {
    return restTemplate.exchange(
        "/students/" + student.getId() + "/grades",
        GET,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  private ResponseEntity<Grade> gradeById(UUID id, String token) {
    return restTemplate.exchange(
        "/grades/" + id, GET, new HttpEntity<>(bearer(token)), Grade.class);
  }

  private ResponseEntity<String> gradeByIdRaw(UUID id, String token) {
    return restTemplate.exchange(
        "/grades/" + id, GET, new HttpEntity<>(bearer(token)), String.class);
  }

  private ResponseEntity<List<GradeHistory>> gradeHistory(UUID id, String token) {
    return restTemplate.exchange(
        "/grades/" + id + "/history",
        GET,
        new HttpEntity<>(bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  // --- writing ------------------------------------------------------------------

  @Test
  void writing_grades_requires_authentication() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/exams/" + UUID.randomUUID() + "/grades"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("[]"))
            .build();

    assertEquals(401, HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  @Test
  void a_first_grade_is_recorded_as_a_creation() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();

    var saved = put(exam, List.of(creation(student.getId(), "14.00")), admin).getBody().get(0);

    assertEquals(0, new BigDecimal("14.00").compareTo(saved.getValue()));
    assertEquals(student.getId(), saved.getStudentId());
  }

  @Test
  void a_first_grade_with_the_wrong_reason_type_is_refused() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();

    var response =
        putRaw(
            exam,
            List.of(update(student.getId(), "14.00", GradeChangeReasonType.CLAIM, "Because")),
            admin);

    assertEquals(400, response.getStatusCode().value());
  }

  @Test
  void an_update_records_the_previous_value_in_history() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    var created = put(exam, List.of(creation(student.getId(), "10.00")), admin).getBody().get(0);

    put(
        exam,
        List.of(update(student.getId(), "14.00", GradeChangeReasonType.CLAIM, "Claim accepted")),
        admin);

    var history = gradeHistory(created.getId(), admin).getBody();
    assertEquals(2, history.size());
    assertEquals(0, new BigDecimal("14.00").compareTo(history.get(0).getNewValue()));
    assertEquals(0, new BigDecimal("10.00").compareTo(history.get(0).getOldValue()));
  }

  @Test
  void an_update_with_reason_type_creation_is_refused() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    put(exam, List.of(creation(student.getId(), "10.00")), admin);

    var response = putRaw(exam, List.of(creation(student.getId(), "14.00")), admin);

    assertEquals(400, response.getStatusCode().value());
  }

  @Test
  void submitting_the_same_value_again_is_refused() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    put(exam, List.of(creation(student.getId(), "14.00")), admin);

    var response =
        putRaw(
            exam,
            List.of(
                update(student.getId(), "14.00", GradeChangeReasonType.CORRECTION, "No change")),
            admin);

    assertEquals(400, response.getStatusCode().value());
  }

  @Test
  void a_grade_out_of_range_is_refused() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();

    assertEquals(
        400,
        putRaw(exam, List.of(creation(student.getId(), "20.50")), admin).getStatusCode().value());
  }

  @Test
  void a_missing_reason_is_refused() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();

    var response =
        putRaw(
            exam,
            List.of(update(student.getId(), "14.00", GradeChangeReasonType.CREATION, " ")),
            admin);

    assertEquals(400, response.getStatusCode().value());
  }

  @Test
  void only_an_admin_or_an_assigned_teacher_can_write_grades() {
    var course = course(SemesterRef.S1);
    var exam = exam(course, "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();

    assertEquals(
        403,
        putRaw(exam, List.of(creation(student.getId(), "14.00")), tokenFor(Role.STUDENT))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        putRaw(exam, List.of(creation(student.getId(), "14.00")), teacherAccount().token())
            .getStatusCode()
            .value(),
        "unassigned teacher");
  }

  @Test
  void an_assigned_teacher_can_write_grades() {
    var course = course(SemesterRef.S1);
    var exam = exam(course, "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    var teacher = teacherAccount();
    assignTeaching(course, teacher.teacher(), group(promotion()));

    var response = put(exam, List.of(creation(student.getId(), "14.00")), teacher.token());

    assertEquals(OK, response.getStatusCode());
  }

  @Test
  void a_track_not_selected_grade_is_conflicted() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S5), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    // No track choice recorded for this student: S5 expects one.

    assertEquals(
        CONFLICT, putRaw(exam, List.of(creation(student.getId(), "14.00")), admin).getStatusCode());
  }

  @Test
  void a_grade_is_accepted_once_the_student_has_chosen_a_track() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S5), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    chooseTrack(student);

    assertEquals(OK, put(exam, List.of(creation(student.getId(), "14.00")), admin).getStatusCode());
  }

  // --- reading a student's grades -------------------------------------------------

  @Test
  void a_student_reads_their_own_grades() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var studentAccount = studentAccount(promotion());
    put(exam, List.of(creation(studentAccount.student().getId(), "14.00")), admin);

    var found = studentGrades(studentAccount.student(), null, studentAccount.token());

    assertEquals(OK, found.getStatusCode());
    assertEquals(1, found.getBody().size());
  }

  @Test
  void a_student_cannot_read_another_student_s_grades() {
    var studentAccount = studentAccount(promotion());
    var another = studentAccount(promotion());

    assertEquals(
        403, studentGradesRaw(studentAccount.student(), another.token()).getStatusCode().value());
  }

  @Test
  void a_student_s_grades_are_filtered_by_semester() {
    var admin = tokenFor(Role.ADMIN);
    var studentAccount = studentAccount(promotion());
    put(
        exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z"),
        List.of(creation(studentAccount.student().getId(), "14.00")),
        admin);
    put(
        exam(course(SemesterRef.S2), "2026-01-15T08:00:00Z"),
        List.of(creation(studentAccount.student().getId(), "10.00")),
        admin);

    var found = studentGrades(studentAccount.student(), "S1", admin).getBody();

    assertEquals(1, found.size());
  }

  // --- reading an exam's grades, filtered by covered group -------------------------

  @Test
  void an_admin_sees_every_grade_of_an_exam() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var course = course(SemesterRef.S1);
    var exam = exam(course, "2026-01-15T08:00:00Z");
    var groupA = group(promotion);
    var groupB = group(promotion);
    var studentA = studentAccount(promotion).student();
    var studentB = studentAccount(promotion).student();
    assignToGroup(studentA, groupA, "2020-01-01");
    assignToGroup(studentB, groupB, "2020-01-01");
    put(
        exam,
        List.of(creation(studentA.getId(), "14.00"), creation(studentB.getId(), "10.00")),
        admin);

    assertEquals(2, examGrades(exam, admin).getBody().size());
  }

  @Test
  void a_teacher_only_sees_grades_of_students_in_a_group_they_cover_on_the_exam_date() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var course = course(SemesterRef.S1);
    var exam = exam(course, "2026-01-15T08:00:00Z");
    var groupA = group(promotion);
    var groupB = group(promotion);
    var teacher = teacherAccount();
    assignTeaching(course, teacher.teacher(), groupA);
    var studentA = studentAccount(promotion).student();
    var studentB = studentAccount(promotion).student();
    assignToGroup(studentA, groupA, "2020-01-01");
    assignToGroup(studentB, groupB, "2020-01-01");
    put(
        exam,
        List.of(creation(studentA.getId(), "14.00"), creation(studentB.getId(), "10.00")),
        admin);

    var seen = examGrades(exam, teacher.token()).getBody();

    assertEquals(1, seen.size());
    assertEquals(studentA.getId(), seen.get(0).getStudentId());
  }

  @Test
  void a_student_never_reads_an_exam_s_grade_list() {
    var course = course(SemesterRef.S1);
    var exam = exam(course, "2026-01-15T08:00:00Z");

    assertEquals(
        403,
        restTemplate
            .exchange(
                "/exams/" + exam.getId() + "/grades",
                GET,
                new HttpEntity<>(bearer(tokenFor(Role.STUDENT))),
                String.class)
            .getStatusCode()
            .value());
  }

  // --- a single grade and its history ----------------------------------------------

  @Test
  void an_unknown_grade_is_not_found() {
    assertEquals(NOT_FOUND, gradeById(UUID.randomUUID(), tokenFor(Role.ADMIN)).getStatusCode());
  }

  @Test
  void an_unrelated_student_cannot_read_a_grade() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var owner = studentAccount(promotion());
    var created =
        put(exam, List.of(creation(owner.student().getId(), "14.00")), admin).getBody().get(0);
    var stranger = studentAccount(promotion());

    assertEquals(403, gradeByIdRaw(created.getId(), stranger.token()).getStatusCode().value());
  }

  @Test
  void the_owning_student_reads_their_grade() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var owner = studentAccount(promotion());
    var created =
        put(exam, List.of(creation(owner.student().getId(), "14.00")), admin).getBody().get(0);

    assertEquals(OK, gradeById(created.getId(), owner.token()).getStatusCode());
  }

  @Test
  void grades_are_serialized_in_snake_case() {
    var admin = tokenFor(Role.ADMIN);
    var exam = exam(course(SemesterRef.S1), "2026-01-15T08:00:00Z");
    var student = studentAccount(promotion()).student();
    var created = put(exam, List.of(creation(student.getId(), "14.00")), admin).getBody().get(0);

    var raw = gradeByIdRaw(created.getId(), admin).getBody();

    assertTrue(raw.contains("\"student_id\""), "body was " + raw);
    assertTrue(raw.contains("\"updated_at\""), "body was " + raw);
  }
}
