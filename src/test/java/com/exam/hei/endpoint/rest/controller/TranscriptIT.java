package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.event.EventProducer;
import com.exam.hei.endpoint.rest.model.TranscriptRequest;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.file.hash.FileHash;
import com.exam.hei.file.hash.FileHashAlgorithm;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class TranscriptIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

  @MockBean BucketComponent bucketComponent;

  @MockBean EventProducer<?> eventProducer;

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @BeforeEach
  void uploadsSucceed() {
    when(bucketComponent.upload(any(), anyString()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "hash"));
  }

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private record Account(Student student, String token) {}

  private Account studentAccount() {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
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
    return new Account(student, jwtService.issue(user).token());
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

  private static HttpHeaders bearer(String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.set(AUTHORIZATION, "Bearer " + token);
    }
    return headers;
  }

  private HttpResponse<String> post(UUID studentId, String semesterRef, String token)
      throws Exception {
    var body =
        semesterRef == null
            ? "{}"
            : objectMapper.writeValueAsString(java.util.Map.of("semester_ref", semesterRef));
    var builder =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    restTemplate.getRootUri() + "/students/" + studentId + "/transcript-requests"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private TranscriptRequest parse(String json) throws Exception {
    return objectMapper.readValue(json, TranscriptRequest.class);
  }

  private ResponseEntity<TranscriptRequest> byId(UUID id, String token) {
    return restTemplate.exchange(
        "/transcript-requests/" + id,
        GET,
        new HttpEntity<>(bearer(token)),
        TranscriptRequest.class);
  }

  private ResponseEntity<List<TranscriptRequest>> listOf(UUID studentId, String token) {
    return restTemplate.exchange(
        "/students/" + studentId + "/transcript-requests",
        GET,
        new HttpEntity<>(bearer(token)),
        new ParameterizedTypeReference<>() {});
  }

  @Test
  void requesting_a_transcript_is_accepted_not_completed() throws Exception {
    var account = studentAccount();

    var response = post(account.student().getId(), null, account.token());

    assertEquals(202, response.statusCode(), "body was " + response.body());
  }

  @Test
  void an_accepted_request_is_generated_and_carries_no_link_yet() throws Exception {
    var account = studentAccount();

    var created = parse(post(account.student().getId(), null, account.token()).body());

    assertEquals(TranscriptStatus.GENERATED, created.getStatus());
    assertNotNull(created.getGeneratedAt());
    assertNull(created.getFileUrl(), "the link is minted by the consumer, not here");
    assertNull(created.getSentAt());
  }

  @Test
  void a_student_may_only_request_their_own_transcript() throws Exception {
    var owner = studentAccount();
    var stranger = studentAccount();

    var response = post(owner.student().getId(), null, stranger.token());

    assertEquals(403, response.statusCode(), "body was " + response.body());
  }

  @Test
  void an_admin_may_request_for_a_student() throws Exception {
    var account = studentAccount();

    assertEquals(202, post(account.student().getId(), null, adminToken()).statusCode());
  }

  @Test
  void requesting_without_a_token_is_unauthorized() throws Exception {
    var account = studentAccount();

    assertEquals(401, post(account.student().getId(), null, null).statusCode());
  }

  @Test
  void requesting_for_an_unknown_student_is_not_found() throws Exception {
    assertEquals(404, post(UUID.randomUUID(), null, adminToken()).statusCode());
  }

  @Test
  void a_single_semester_request_keeps_its_scope() throws Exception {
    var account = studentAccount();

    var created = parse(post(account.student().getId(), "S5", account.token()).body());

    assertEquals(com.exam.hei.repository.model.SemesterRef.S5, created.getSemesterRef());
  }

  @Test
  void a_full_curriculum_request_names_no_semester() throws Exception {
    var account = studentAccount();

    var created = parse(post(account.student().getId(), null, account.token()).body());

    assertNull(created.getSemesterRef());
  }

  @Test
  void a_request_is_readable_by_its_id() throws Exception {
    var account = studentAccount();
    var created = parse(post(account.student().getId(), null, account.token()).body());

    var found = byId(created.getId(), account.token());

    assertEquals(OK, found.getStatusCode());
    assertEquals(created.getId(), found.getBody().getId());
  }

  @Test
  void an_unknown_request_is_not_found() {
    assertEquals(NOT_FOUND, byId(UUID.randomUUID(), adminToken()).getStatusCode());
  }

  @Test
  void a_student_cannot_read_another_student_s_request() throws Exception {
    var owner = studentAccount();
    var created = parse(post(owner.student().getId(), null, owner.token()).body());
    var stranger = studentAccount();

    var response =
        restTemplate.exchange(
            "/transcript-requests/" + created.getId(),
            GET,
            new HttpEntity<>(bearer(stranger.token())),
            String.class);

    assertEquals(403, response.getStatusCode().value());
  }

  @Test
  void a_student_lists_their_own_requests_most_recent_first() throws Exception {
    var account = studentAccount();
    post(account.student().getId(), null, account.token());
    post(account.student().getId(), "S5", account.token());

    var found = listOf(account.student().getId(), account.token());

    assertEquals(OK, found.getStatusCode());
    assertEquals(2, found.getBody().size());
    assertTrue(
        !found.getBody().get(0).getRequestedAt().isBefore(found.getBody().get(1).getRequestedAt()),
        "most recent first");
  }

  @Test
  void a_student_cannot_list_another_student_s_requests() {
    var owner = studentAccount();
    var stranger = studentAccount();

    var response =
        restTemplate.exchange(
            "/students/" + owner.student().getId() + "/transcript-requests",
            GET,
            new HttpEntity<>(bearer(stranger.token())),
            String.class);

    assertEquals(403, response.getStatusCode().value());
  }

  @Test
  void transcript_requests_are_serialized_in_snake_case() throws Exception {
    var account = studentAccount();
    var created = parse(post(account.student().getId(), null, account.token()).body());

    var raw =
        restTemplate
            .exchange(
                "/transcript-requests/" + created.getId(),
                GET,
                new HttpEntity<>(bearer(account.token())),
                String.class)
            .getBody();

    assertTrue(raw.contains("\"student_id\""), "body was " + raw);
    assertTrue(raw.contains("\"requested_at\""), "body was " + raw);
    assertTrue(raw.contains("\"file_url\""), "body was " + raw);
  }

  @Test
  void the_bucket_key_is_never_exposed() throws Exception {
    var account = studentAccount();
    var created = parse(post(account.student().getId(), null, account.token()).body());

    var raw =
        restTemplate
            .exchange(
                "/transcript-requests/" + created.getId(),
                GET,
                new HttpEntity<>(bearer(account.token())),
                String.class)
            .getBody();

    assertTrue(!raw.contains("s3_key"), "body was " + raw);
    assertTrue(!raw.contains("transcripts/"), "body was " + raw);
  }
}
