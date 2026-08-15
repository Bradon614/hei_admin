package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.Course;
import com.exam.hei.endpoint.rest.model.StudentTrackChoiceCreation;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Track;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

/**
 * The critical rule, end to end, against a real curriculum in the database: an EL student never
 * sees a TN course, and the other way round.
 */
class StudentCourseIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired JwtService jwtService;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private String adminKey() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.ADMIN)
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

  private Track el() {
    return trackRepository.findByCode("EL").orElseThrow();
  }

  private Track tn() {
    return trackRepository.findByCode("TN").orElseThrow();
  }

  private Student student() {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .tracks(List.of(el(), tn()))
                .build());
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(account)
            .build());
  }

  private com.exam.hei.repository.model.Course course(
      SemesterRef semesterRef, Track track, int credits, String label) {
    return courseRepository.save(
        com.exam.hei.repository.model.Course.builder()
            .ref(label + "-" + rand(10))
            .title(label)
            .credits(credits)
            .semester(semesterRepository.findByRef(semesterRef).orElseThrow())
            .track(track)
            .build());
  }

  private void chooses(Student student, Track track, String adminKey) {
    restTemplate.exchange(
        "/students/" + student.getId() + "/track-choices",
        POST,
        new HttpEntity<>(
            StudentTrackChoiceCreation.builder()
                .trackId(track.getId())
                .fromSemesterRef(SemesterRef.S4)
                .build(),
            bearer(adminKey)),
        String.class);
  }

  private List<Course> coursesOf(Student student, SemesterRef semesterRef, String apiKey) {
    var url =
        "/students/"
            + student.getId()
            + "/courses"
            + (semesterRef == null ? "" : "?semester_ref=" + semesterRef);
    return restTemplate
        .exchange(
            url,
            GET,
            new HttpEntity<>(bearer(apiKey)),
            new ParameterizedTypeReference<List<Course>>() {})
        .getBody();
  }

  /** Scoped to this test's own courses: the Postgres container is shared. */
  private List<Course> only(List<Course> courses, Set<UUID> mine) {
    return courses.stream().filter(course -> mine.contains(course.getId())).toList();
  }

  private int creditsOf(List<Course> courses) {
    return courses.stream().mapToInt(Course::getCredits).sum();
  }

  // --- the critical rule ----------------------------------------------------

  @Test
  void an_el_student_follows_the_common_and_el_courses_and_never_a_tn_one() {
    var admin = adminKey();
    var jean = student();
    var common = course(SemesterRef.S5, null, 12, "MATH5");
    var forEl = course(SemesterRef.S5, el(), 18, "PROG-AV");
    var forTn = course(SemesterRef.S5, tn(), 18, "TRANSFO");
    chooses(jean, el(), admin);

    var followed = coursesOf(jean, SemesterRef.S5, admin).stream().map(Course::getId).toList();

    assertTrue(followed.contains(common.getId()), "a common course belongs to every programme");
    assertTrue(followed.contains(forEl.getId()));
    assertFalse(followed.contains(forTn.getId()), "an EL student never sees a TN course");
  }

  @Test
  void a_tn_student_sees_the_mirror_image() {
    var admin = adminKey();
    var alice = student();
    var common = course(SemesterRef.S5, null, 12, "MATH5");
    var forEl = course(SemesterRef.S5, el(), 18, "PROG-AV");
    var forTn = course(SemesterRef.S5, tn(), 18, "TRANSFO");
    chooses(alice, tn(), admin);

    var followed = coursesOf(alice, SemesterRef.S5, admin).stream().map(Course::getId).toList();

    assertTrue(followed.contains(common.getId()));
    assertTrue(followed.contains(forTn.getId()));
    assertFalse(followed.contains(forEl.getId()));
  }

  @Test
  void each_programme_is_worth_thirty_credits_over_the_same_semester() {
    // 12 common + 18 on each side: 30 per programme, 48 across the semester.
    var admin = adminKey();
    var jean = student();
    var alice = student();
    var common = course(SemesterRef.S6, null, 12, "PROJ6");
    var forEl = course(SemesterRef.S6, el(), 18, "ARCHI");
    var forTn = course(SemesterRef.S6, tn(), 18, "PILOTAGE");
    var mine = Set.of(common.getId(), forEl.getId(), forTn.getId());
    chooses(jean, el(), admin);
    chooses(alice, tn(), admin);

    var creditsForEl = creditsOf(only(coursesOf(jean, SemesterRef.S6, admin), mine));
    var creditsForTn = creditsOf(only(coursesOf(alice, SemesterRef.S6, admin), mine));

    assertEquals(30, creditsForEl);
    assertEquals(30, creditsForTn);
  }

  // --- common core ----------------------------------------------------------

  @Test
  void every_student_follows_the_same_common_core() {
    var admin = adminKey();
    var jean = student();
    var alice = student();
    var maths = course(SemesterRef.S2, null, 15, "MATH2");
    var algo = course(SemesterRef.S2, null, 15, "ALGO2");
    var mine = Set.of(maths.getId(), algo.getId());
    chooses(jean, el(), admin);

    var forJean = only(coursesOf(jean, SemesterRef.S2, admin), mine);
    var forAlice = only(coursesOf(alice, SemesterRef.S2, admin), mine);

    assertEquals(2, forJean.size());
    assertEquals(forJean, forAlice, "the common core does not depend on any track");
    assertEquals(30, creditsOf(forJean));
  }

  // --- no track chosen ------------------------------------------------------

  @Test
  void a_student_without_a_track_follows_nothing_from_the_track_semesters() {
    var admin = adminKey();
    var jean = student();
    course(SemesterRef.S5, null, 12, "MATH5");
    course(SemesterRef.S5, el(), 18, "PROG-AV");
    course(SemesterRef.S5, tn(), 18, "TRANSFO");

    assertTrue(coursesOf(jean, SemesterRef.S5, admin).isEmpty());
  }

  @Test
  void a_student_without_a_track_still_follows_the_common_core() {
    var admin = adminKey();
    var jean = student();
    var maths = course(SemesterRef.S3, null, 30, "MATH3");

    var followed = only(coursesOf(jean, SemesterRef.S3, admin), Set.of(maths.getId()));

    assertEquals(1, followed.size());
  }

  @Test
  void the_whole_curriculum_resolves_each_semester_on_its_own() {
    var admin = adminKey();
    var jean = student();
    var inCommonCore = course(SemesterRef.S1, null, 30, "MATH1");
    var forEl = course(SemesterRef.S4, el(), 18, "PROG4");
    var forTn = course(SemesterRef.S4, tn(), 18, "TRANSFO4");
    chooses(jean, el(), admin);
    var mine = Set.of(inCommonCore.getId(), forEl.getId(), forTn.getId());

    var whole = only(coursesOf(jean, null, admin), mine).stream().map(Course::getId).toList();

    assertTrue(whole.contains(inCommonCore.getId()));
    assertTrue(whole.contains(forEl.getId()));
    assertFalse(whole.contains(forTn.getId()));
  }

  // --- authorization --------------------------------------------------------

  @Test
  void reading_a_curriculum_requires_authentication() {
    var jean = student();

    assertEquals(
        UNAUTHORIZED,
        restTemplate
            .exchange(
                "/students/" + jean.getId() + "/courses",
                GET,
                new HttpEntity<>(bearer(null)),
                String.class)
            .getStatusCode());
  }

  @Test
  void a_student_reads_their_own_curriculum() {
    var admin = adminKey();
    var jean = student();
    var maths = course(SemesterRef.S1, null, 30, "MATH1");
    var jeanKey =
        jwtService.issue(studentRepository.findById(jean.getId()).orElseThrow().getUser()).token();

    assertEquals(1, only(coursesOf(jean, SemesterRef.S1, jeanKey), Set.of(maths.getId())).size());
  }

  @Test
  void a_student_cannot_read_the_curriculum_of_another_student() {
    var jean = student();
    var alice = student();
    var aliceKey =
        jwtService.issue(studentRepository.findById(alice.getId()).orElseThrow().getUser()).token();

    var response =
        restTemplate.exchange(
            "/students/" + jean.getId() + "/courses",
            GET,
            new HttpEntity<>(bearer(aliceKey)),
            String.class);

    assertEquals(403, response.getStatusCode().value());
  }
}
