package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.model.GradeChange;
import com.exam.hei.endpoint.rest.model.Graduate;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.StudentTrackChoiceRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentTrackChoice;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class GraduateIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired ExamRepository examRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired StudentTrackChoiceRepository studentTrackChoiceRepository;
  @Autowired TrackRepository trackRepository;
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

  private Student student(Promotion promotion, String lastName, String firstName) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName(firstName)
            .lastName(lastName)
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(user)
            .build());
  }

  private Exam soleExamOf(SemesterRef semesterRef) {
    var course =
        courseRepository.save(
            Course.builder()
                .ref(rand(20))
                .title("Course under test")
                .credits(30)
                .semester(semesterRepository.findByRef(semesterRef).orElseThrow())
                .build());
    return examRepository.save(
        Exam.builder()
            .course(course)
            .title("Final")
            .dateExam(Instant.parse("2026-01-15T08:00:00Z"))
            .coefficient(new BigDecimal("1.00"))
            .build());
  }

  private void grade(Exam exam, UUID studentId, String value, String admin) {
    restTemplate.exchange(
        "/exams/" + exam.getId() + "/grades",
        PUT,
        new HttpEntity<>(
            List.of(
                GradeChange.builder()
                    .studentId(studentId)
                    .value(new BigDecimal(value))
                    .reasonType(GradeChangeReasonType.CREATION)
                    .reason("Initial entry")
                    .build()),
            bearer(admin)),
        String.class);
  }

  private void chooseElFromS4(Student student) {
    studentTrackChoiceRepository.save(
        StudentTrackChoice.builder()
            .student(student)
            .track(trackRepository.findByCode("EL").orElseThrow())
            .fromSemester(semesterRepository.findByRef(SemesterRef.S4).orElseThrow())
            .build());
  }

  private void graduate(Student student, String average, String admin) {
    chooseElFromS4(student);
    for (var ref : SemesterRef.values()) {
      grade(soleExamOf(ref), student.getId(), average, admin);
    }
  }

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
    }
    return headers;
  }

  private ResponseEntity<List<Graduate>> graduates(Promotion promotion, String token) {
    return restTemplate.exchange(
        "/promotions/" + promotion.getId() + "/graduates",
        GET,
        new HttpEntity<>(bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> graduatesRaw(Promotion promotion, String token) {
    return restTemplate.exchange(
        "/promotions/" + promotion.getId() + "/graduates",
        GET,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  private ResponseEntity<byte[]> excel(Promotion promotion, String token) {
    return restTemplate.exchange(
        "/promotions/" + promotion.getId() + "/graduates/excel",
        GET,
        new HttpEntity<>(bearer(token)),
        byte[].class);
  }

  @Test
  void only_an_admin_reads_the_graduate_list() {
    var promotion = promotion();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403, graduatesRaw(promotion, tokenFor(role)).getStatusCode().value(), "role " + role);
    }
  }

  @Test
  void an_unknown_promotion_is_not_found() {
    var admin = tokenFor(Role.ADMIN);

    assertEquals(
        NOT_FOUND,
        restTemplate
            .exchange(
                "/promotions/" + UUID.randomUUID() + "/graduates",
                GET,
                new HttpEntity<>(bearer(admin)),
                String.class)
            .getStatusCode());
  }

  @Test
  void graduates_are_ranked_and_ties_share_a_rank() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var top = student(promotion, "Rakoto", "Jean");
    var tiedA = student(promotion, "Andria", "Zo");
    var tiedB = student(promotion, "Zafy", "Ary");
    graduate(top, "16.00", admin);
    graduate(tiedA, "12.00", admin);
    graduate(tiedB, "12.00", admin);

    var found = graduates(promotion, admin).getBody();

    assertEquals(OK, graduates(promotion, admin).getStatusCode());
    assertEquals(3, found.size());
    assertEquals(1, found.get(0).getRank());
    assertEquals(top.getRef(), found.get(0).getStd());
    assertEquals(2, found.get(1).getRank());
    assertEquals("Andria", found.get(1).getLastName());
    assertEquals(2, found.get(2).getRank());
    assertEquals("Zafy", found.get(2).getLastName());
    assertEquals("EL", found.get(0).getTrackCode());
  }

  @Test
  void a_student_who_failed_a_semester_is_not_a_graduate() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var failed = student(promotion, "Rakoto", "Jean");
    chooseElFromS4(failed);
    grade(soleExamOf(SemesterRef.S1), failed.getId(), "5.00", admin);

    assertTrue(graduates(promotion, admin).getBody().isEmpty());
  }

  @Test
  void graduates_are_serialized_in_snake_case() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    graduate(student(promotion, "Rakoto", "Jean"), "14.00", admin);

    var raw = graduatesRaw(promotion, admin).getBody();

    assertTrue(raw.contains("\"general_average\""), "body was " + raw);
    assertTrue(raw.contains("\"track_code\""), "body was " + raw);
  }

  // --- Excel export -----------------------------------------------------------

  @Test
  void only_an_admin_downloads_the_graduate_excel_file() {
    var promotion = promotion();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          restTemplate
              .exchange(
                  "/promotions/" + promotion.getId() + "/graduates/excel",
                  GET,
                  new HttpEntity<>(bearer(tokenFor(role))),
                  String.class)
              .getStatusCode()
              .value(),
          "role " + role);
    }
  }

  @Test
  void downloading_the_excel_of_an_unknown_promotion_is_not_found() {
    var admin = tokenFor(Role.ADMIN);

    assertEquals(
        NOT_FOUND,
        restTemplate
            .exchange(
                "/promotions/" + UUID.randomUUID() + "/graduates/excel",
                GET,
                new HttpEntity<>(bearer(admin)),
                String.class)
            .getStatusCode());
  }

  @Test
  void the_excel_file_names_itself_after_the_promotion_and_the_correct_content_type() {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    graduate(student(promotion, "Rakoto", "Jean"), "14.00", admin);

    var response = excel(promotion, admin);

    assertEquals(OK, response.getStatusCode());
    assertEquals(
        "attachment; filename=\"graduates-" + promotion.getRef() + ".xlsx\"",
        response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    assertEquals(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        response.getHeaders().getContentType().toString());
  }

  @Test
  void the_downloaded_file_carries_the_same_data_as_the_json_listing() throws Exception {
    // Compared against the JSON response rather than a literal: courses are never scoped to a
    // promotion, so the general average of a semester also reflects whichever other courses other
    // tests created there, each counting as a missed exam. Only the JSON listing knows the true
    // value for this run.
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    graduate(student(promotion, "Rakoto", "Jean"), "14.00", admin);
    var expected = graduates(promotion, admin).getBody().get(0);

    var bytes = excel(promotion, admin).getBody();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var row = workbook.getSheetAt(0).getRow(1);
      assertEquals("Rakoto", row.getCell(2).getStringCellValue());
      assertEquals(
          0,
          expected
              .getGeneralAverage()
              .compareTo(BigDecimal.valueOf(row.getCell(4).getNumericCellValue())));
    }
  }
}
