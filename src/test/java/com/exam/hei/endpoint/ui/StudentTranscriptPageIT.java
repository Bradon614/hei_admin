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
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TranscriptRequestRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;

class StudentTranscriptPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TranscriptRequestRepository transcriptRequestRepository;
  @Autowired JwtService jwtService;

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

  private AppUser userOf(Role role) {
    return appUserRepository.save(
        AppUser.builder().email(rand(12) + "@hei.test").passwordHash("hash").role(role).build());
  }

  private String tokenFor(Role role) {
    return jwtService.issue(userOf(role)).token();
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

  private HttpResponse<String> get(String path, String cookieToken) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(restTemplate.getRootUri() + path));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> requestTranscript(UUID studentId, String scope, String cookieToken)
      throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    restTemplate.getRootUri() + "/ui/admin/students/" + studentId + "/transcripts"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "semester_ref=" + URLEncoder.encode(scope, StandardCharsets.UTF_8)));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void the_page_names_the_student_and_the_address_the_transcript_will_reach() throws Exception {
    var student = student();

    var body = get("/ui/admin/students/" + student.getId() + "/transcripts", tokenFor(Role.ADMIN));

    assertEquals(200, body.statusCode());
    assertTrue(body.body().contains(student.getRef()), "body was " + body.body());
    assertTrue(body.body().contains(student.getEmail()), "the recipient must be shown");
  }

  @Test
  void an_admin_requests_a_transcript_for_any_student() throws Exception {
    var student = student();

    var response = requestTranscript(student.getId(), "", tokenFor(Role.ADMIN));

    assertEquals(302, response.statusCode(), "body was " + response.body());
    var stored =
        transcriptRequestRepository.findAllByStudentIdOrderByRequestedAtDesc(student.getId());
    assertEquals(1, stored.size());
    assertEquals(student.getId(), stored.get(0).getStudent().getId());
  }

  @Test
  void the_request_is_confirmed_on_the_page_it_redirects_to() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var student = student();

    var response = requestTranscript(student.getId(), "", admin);
    var location = response.headers().firstValue("Location").orElseThrow();

    assertTrue(
        location.contains("done=transcript-requested-for-student"), "location was " + location);
    assertTrue(
        get(location.substring(location.indexOf("/ui/")), admin).body().contains("to the student"),
        "the confirmation must say the student is the recipient");
  }

  @Test
  void a_single_semester_request_keeps_its_scope_on_the_page() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var student = student();

    requestTranscript(student.getId(), "S5", admin);

    var body = get("/ui/admin/students/" + student.getId() + "/transcripts", admin).body();
    assertTrue(body.contains("S5"), "body was " + body);
  }

  @Test
  void the_page_shows_the_status_of_every_request() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var student = student();
    requestTranscript(student.getId(), "", admin);

    var body = get("/ui/admin/students/" + student.getId() + "/transcripts", admin).body();

    assertTrue(body.contains("GENERATED"), "body was " + body);
  }

  @Test
  void a_failed_request_shows_its_status_and_its_reason() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var student = student();
    requestTranscript(student.getId(), "", admin);
    var stored =
        transcriptRequestRepository
            .findAllByStudentIdOrderByRequestedAtDesc(student.getId())
            .get(0);
    stored.setStatus(TranscriptStatus.FAILED);
    stored.setErrorMessage("The transcript could not be emailed");
    transcriptRequestRepository.save(stored);

    var body = get("/ui/admin/students/" + student.getId() + "/transcripts", admin).body();

    assertTrue(body.contains("FAILED"), "body was " + body);
    assertTrue(body.contains("could not be emailed"), "the reason must be shown");
  }

  @Test
  void a_sent_request_offers_the_link_and_the_date_it_went_out() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var student = student();
    requestTranscript(student.getId(), "", admin);
    var stored =
        transcriptRequestRepository
            .findAllByStudentIdOrderByRequestedAtDesc(student.getId())
            .get(0);
    stored.setStatus(TranscriptStatus.SENT);
    stored.setFileUrl("https://example.test/presigned-transcript");
    stored.setSentAt(Instant.parse("2026-01-15T10:00:00Z"));
    transcriptRequestRepository.save(stored);

    var body = get("/ui/admin/students/" + student.getId() + "/transcripts", admin).body();

    assertTrue(body.contains("SENT"), "body was " + body);
    assertTrue(
        body.contains("https://example.test/presigned-transcript"), "the link must be shown");
    assertTrue(body.contains("2026-01-15"), "the send date must be shown");
  }

  @Test
  void a_student_with_no_request_is_told_so_rather_than_shown_an_empty_table() throws Exception {
    var body =
        get("/ui/admin/students/" + student().getId() + "/transcripts", tokenFor(Role.ADMIN))
            .body();

    assertTrue(body.contains("No transcript requested for this student yet"), "body was " + body);
    assertTrue(!body.contains("<table"), "an empty table would read as a rendering bug");
  }

  @Test
  void an_unknown_student_is_reported_rather_than_thrown() throws Exception {
    var response =
        get("/ui/admin/students/" + UUID.randomUUID() + "/transcripts", tokenFor(Role.ADMIN));

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("not found"), "body was " + response.body());
  }

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    var student = student();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          get("/ui/admin/students/" + student.getId() + "/transcripts", tokenFor(role))
              .statusCode(),
          "role " + role);
      assertEquals(
          403, requestTranscript(student.getId(), "", tokenFor(role)).statusCode(), "role " + role);
    }
    assertTrue(
        transcriptRequestRepository
            .findAllByStudentIdOrderByRequestedAtDesc(student.getId())
            .isEmpty(),
        "nothing must have been created");
  }

  @Test
  void the_students_listing_links_to_the_transcripts_of_each_row() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var student = student();

    var body =
        get("/ui/admin/students?promotion_id=" + student.getPromotion().getId(), admin).body();

    assertTrue(
        body.contains("/ui/admin/students/" + student.getId() + "/transcripts"),
        "the row must offer the transcripts screen");
  }
}
