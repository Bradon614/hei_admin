package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.event.EventProducer;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.file.hash.FileHash;
import com.exam.hei.file.hash.FileHashAlgorithm;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;

class MePageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired TeachingAssignmentRepository teachingAssignmentRepository;
  @Autowired JwtService jwtService;

  /** Never the real one: the context wires it to eu-west-3, and a click must not reach AWS. */
  @MockBean BucketComponent bucketComponent;

  @MockBean EventProducer<?> eventProducer;

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(NEVER).build();

  @BeforeEach
  void uploadsSucceed() {
    when(bucketComponent.upload(any(), anyString()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "hash"));
  }

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private record Account(UUID profileId, String token) {}

  private Promotion promotion() {
    return promotionRepository.save(
        Promotion.builder()
            .ref(TestRefs.promotionRef())
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .build());
  }

  private Account studentAccount() {
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
                .ref(rand(18))
                .firstName("Jean")
                .lastName("Rakoto")
                .email(rand(12) + "@hei.test")
                .entranceDate(LocalDate.of(2025, 9, 1))
                .promotion(promotion())
                .user(user)
                .build());
    return new Account(student.getId(), jwtService.issue(user).token());
  }

  private Account teacherAccount() {
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
                .ref(rand(18))
                .firstName("Aina")
                .lastName("Randria")
                .email(rand(12) + "@hei.test")
                .user(user)
                .build());
    return new Account(teacher.getId(), jwtService.issue(user).token());
  }

  private String adminToken() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .build());
    return jwtService.issue(user).token();
  }

  private HttpResponse<String> get(String path, String cookieToken) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(restTemplate.getRootUri() + path));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> requestTranscript(String scope, String cookieToken)
      throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/ui/me/transcripts"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("semester_ref=" + scope));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  // --- the student space -------------------------------------------------------------

  @Test
  void a_student_sees_their_own_identity_and_result() throws Exception {
    var student = studentAccount();

    var body = get("/ui/me", student.token()).body();

    assertTrue(body.contains("My result"), "the result section is missing");
    assertTrue(body.contains("NOT GRANTED"), "a student with no grade has no diploma");
    assertTrue(body.contains("STUDENT"), "the role should be shown");
  }

  @Test
  void a_student_with_no_track_choice_is_told_what_blocks_the_diploma() throws Exception {
    // The distinction the whole model rests on: not evaluable is not the same as failed.
    var student = studentAccount();

    var body = get("/ui/me", student.token()).body();

    assertTrue(
        body.contains("TRACK_NOT_SELECTED") || body.contains("No track selected"),
        "the blocker should be spelled out, body was "
            + body.substring(0, Math.min(400, body.length())));
  }

  @Test
  void a_student_never_sees_the_administration_links() throws Exception {
    var student = studentAccount();

    var body = get("/ui/me", student.token()).body();

    assertTrue(!body.contains("/ui/admin/students"), "admin links must not be rendered");
    assertTrue(!body.contains("/ui/admin/teachers"), "admin links must not be rendered");
  }

  // --- the teacher space --------------------------------------------------------------

  @Test
  void a_teacher_sees_what_they_teach() throws Exception {
    var teacher = teacherAccount();
    var course =
        courseRepository.save(
            Course.builder()
                .ref(rand(18))
                .title("Programming")
                .credits(6)
                .semester(semesterRepository.findByRef(SemesterRef.S1).orElseThrow())
                .build());
    var group = groupRepository.save(Group.builder().ref(rand(8)).promotion(promotion()).build());
    teachingAssignmentRepository.save(
        TeachingAssignment.builder()
            .course(course)
            .teacher(teacherRepository.findById(teacher.profileId()).orElseThrow())
            .group(group)
            .build());

    var body = get("/ui/me", teacher.token()).body();

    assertTrue(body.contains("What I teach"), "the teaching section is missing");
    assertTrue(body.contains(course.getRef()), "the assigned course should be listed");
    assertTrue(body.contains(group.getRef()), "the group should be listed");
  }

  @Test
  void a_teacher_sees_no_student_result_section() throws Exception {
    var teacher = teacherAccount();

    var body = get("/ui/me", teacher.token()).body();

    assertTrue(!body.contains("My result"), "a teacher has no personal result");
  }

  // --- an account with no profile --------------------------------------------------------

  @Test
  void an_administrator_gets_a_neutral_page_rather_than_an_error() throws Exception {
    // The bootstrapped admin has neither a student nor a teacher profile.
    var response = get("/ui/me", adminToken());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("no student or teacher profile"));
  }

  // --- transcripts -------------------------------------------------------------------------

  @Test
  void a_student_can_request_a_transcript_from_their_page() throws Exception {
    var student = studentAccount();

    var response = requestTranscript("", student.token());

    assertEquals(302, response.statusCode(), "body was " + response.body());
    assertTrue(get("/ui/me", student.token()).body().contains("GENERATED"));
  }

  @Test
  void a_single_semester_transcript_keeps_its_scope() throws Exception {
    var student = studentAccount();

    requestTranscript("S1", student.token());

    assertTrue(get("/ui/me", student.token()).body().contains("S1"));
  }

  @Test
  void an_account_without_a_student_profile_cannot_request_one() throws Exception {
    var response = requestTranscript("", adminToken());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("Only a student can ask"));
  }
}
