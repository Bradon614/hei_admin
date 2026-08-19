package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class StudentAdminPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired JwtService jwtService;
  @Autowired ObjectMapper objectMapper;

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(NEVER).build();

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
            .build());
  }

  private static String form(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .reduce((a, b) -> a + "&" + b)
        .orElse("");
  }

  private HttpResponse<String> post(String path, Map<String, String> fields, String cookieToken)
      throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(fields)));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String cookieToken) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(restTemplate.getRootUri() + path));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> login(String email, String password) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private Map<String, String> newStudent(UUID promotionId, String email) {
    return Map.of(
        "ref", rand(18),
        "first_name", "Jean",
        "last_name", "Rakoto",
        "email", email,
        "password", "student-demo-2025",
        "entrance_date", "2025-09-01",
        "promotion_id", promotionId.toString());
  }

  @Test
  void a_student_created_from_the_form_can_actually_sign_in() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";

    var created = post("/ui/admin/students", newStudent(promotion.getId(), email), admin);
    assertEquals(302, created.statusCode(), "body was " + created.body());

    var signedIn = login(email, "student-demo-2025");

    assertEquals(200, signedIn.statusCode(), "body was " + signedIn.body());
    assertTrue(
        objectMapper.readTree(signedIn.body()).get("access_token").asText().split("\\.").length
            == 3);
  }

  @Test
  void the_created_account_carries_the_student_role() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";

    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);

    assertEquals(Role.STUDENT, appUserRepository.findByEmail(email).orElseThrow().getRole());
  }

  @Test
  void the_password_is_stored_hashed_and_never_as_written() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";

    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);

    var hash = appUserRepository.findByEmail(email).orElseThrow().getPasswordHash();
    assertTrue(hash.startsWith("$2"), "expected a BCrypt hash but was " + hash);
    assertTrue(!hash.contains("student-demo-2025"));
  }

  @Test
  void the_new_student_appears_in_the_listing() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);

    var page = get("/ui/admin/students?promotion_id=" + promotion.getId(), admin);

    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains(email), "listing did not show the student");
  }

  @Test
  void a_successful_creation_is_confirmed_on_the_page_it_redirects_to() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();

    var created =
        post("/ui/admin/students", newStudent(promotion.getId(), rand(12) + "@hei.demo"), admin);

    assertEquals(302, created.statusCode(), "body was " + created.body());
    var location = created.headers().firstValue("Location").orElseThrow();
    assertTrue(location.contains("done=student-created"), "location was " + location);
    assertTrue(
        get(location.substring(location.indexOf("/ui/")), admin).body().contains("Student created"),
        "the confirmation should be rendered on the landing page");
  }

  @Test
  void an_unknown_confirmation_key_renders_no_banner() throws Exception {
    var admin = tokenFor(Role.ADMIN);

    var page = get("/ui/admin/students?done=whatever-i-want", admin);

    assertEquals(200, page.statusCode());
    assertTrue(!page.body().contains("alert-success"), "no banner should be rendered");
    assertTrue(!page.body().contains("whatever-i-want"), "the key must not be echoed");
  }

  @Test
  void an_admin_disables_an_account_from_the_listing() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);
    var student = studentRepository.findByEmail(email).orElseThrow();

    var response =
        post(
            "/ui/admin/students/" + student.getId() + "/account",
            Map.of("enabled", "false", "promotion_id", promotion.getId().toString()),
            admin);

    assertEquals(302, response.statusCode(), "body was " + response.body());
    assertTrue(!appUserRepository.findByEmail(email).orElseThrow().isEnabled());
  }

  @Test
  void the_listing_shows_which_accounts_are_disabled() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);
    var student = studentRepository.findByEmail(email).orElseThrow();
    post(
        "/ui/admin/students/" + student.getId() + "/account",
        Map.of("enabled", "false", "promotion_id", promotion.getId().toString()),
        admin);

    var page = get("/ui/admin/students?promotion_id=" + promotion.getId(), admin);

    assertTrue(page.body().contains("disabled"), "the account state should be visible");
    assertTrue(page.body().contains("Enable"), "the action should offer to enable it again");
  }

  @Test
  void the_account_action_is_closed_to_the_other_roles() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);
    var student = studentRepository.findByEmail(email).orElseThrow();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      var response =
          post(
              "/ui/admin/students/" + student.getId() + "/account",
              Map.of("enabled", "false", "promotion_id", promotion.getId().toString()),
              tokenFor(role));

      assertEquals(403, response.statusCode(), "role " + role);
    }
    assertTrue(appUserRepository.findByEmail(email).orElseThrow().isEnabled());
  }

  @Test
  void a_duplicate_email_re_renders_the_form_instead_of_answering_json() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);

    var again = post("/ui/admin/students", newStudent(promotion.getId(), email), admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("<form"), "the form must come back");
    assertTrue(!again.body().startsWith("{"), "body was " + again.body());
  }

  @Test
  void an_unknown_promotion_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);

    var response =
        post("/ui/admin/students", newStudent(UUID.randomUUID(), rand(12) + "@hei.demo"), admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<form"));
  }

  @Test
  void a_student_can_be_moved_to_a_group_from_the_listing() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotion();
    var email = rand(12) + "@hei.demo";
    post("/ui/admin/students", newStudent(promotion.getId(), email), admin);
    var student = studentRepository.findByEmail(email).orElseThrow();
    var group =
        objectMapper.readTree(
            restTemplate
                .exchange(
                    "/promotions/" + promotion.getId() + "/groups",
                    org.springframework.http.HttpMethod.PUT,
                    new org.springframework.http.HttpEntity<>(
                        List.of(Map.of("ref", rand(8))), bearerHeaders(admin)),
                    String.class)
                .getBody());

    var response =
        post(
            "/ui/admin/students/" + student.getId() + "/group",
            Map.of(
                "group_id",
                group.get(0).get("id").asText(),
                "start_date",
                "2025-09-01",
                "reason",
                "Initial assignment",
                "promotion_id",
                promotion.getId().toString()),
            admin);

    assertEquals(302, response.statusCode(), "body was " + response.body());
  }

  private org.springframework.http.HttpHeaders bearerHeaders(String token) {
    var headers = new org.springframework.http.HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(403, get("/ui/admin/students", tokenFor(role)).statusCode(), "role " + role);
    }
  }

  @Test
  void a_signed_out_visitor_is_sent_to_the_form() throws Exception {
    var response = get("/ui/admin/students", null);

    assertEquals(302, response.statusCode());
    assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/ui/login"));
  }
}
