package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthPageIT extends FacadeIT {
  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(NEVER).build();

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser account(String email, String rawPassword, Role role) {
    return appUserRepository.save(
        AppUser.builder()
            .email(email)
            .passwordHash(ENCODER.encode(rawPassword))
            .role(role)
            .build());
  }

  private static String form(String email, String password) {
    return "email="
        + URLEncoder.encode(email, StandardCharsets.UTF_8)
        + "&password="
        + URLEncoder.encode(password, StandardCharsets.UTF_8);
  }

  private HttpResponse<String> postLogin(String email, String password) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/ui/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(email, password)))
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void the_sign_in_form_is_reachable_without_a_token() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/ui/login"))
            .GET()
            .build();

    var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<form"), "body was " + response.body());
  }

  @Test
  void an_administrator_lands_on_the_administration_page() throws Exception {
    var email = rand(12) + "@hei.test";
    account(email, "correct-horse", Role.ADMIN);

    var response = postLogin(email, "correct-horse");

    assertEquals(302, response.statusCode());
    assertTrue(
        response.headers().firstValue("Location").orElseThrow().endsWith("/ui/admin"),
        "Location was " + response.headers().firstValue("Location"));
  }

  @Test
  void a_student_lands_on_their_own_page() throws Exception {
    // Sending them to /ui/admin would earn a 403 straight after signing in successfully.
    var email = rand(12) + "@hei.test";
    account(email, "correct-horse", Role.STUDENT);

    var response = postLogin(email, "correct-horse");

    assertEquals(302, response.statusCode());
    assertTrue(
        response.headers().firstValue("Location").orElseThrow().endsWith("/ui/me"),
        "Location was " + response.headers().firstValue("Location"));
  }

  @Test
  void a_teacher_lands_on_their_own_page() throws Exception {
    var email = rand(12) + "@hei.test";
    account(email, "correct-horse", Role.TEACHER);

    var response = postLogin(email, "correct-horse");

    assertEquals(302, response.statusCode());
    assertTrue(
        response.headers().firstValue("Location").orElseThrow().endsWith("/ui/me"),
        "Location was " + response.headers().firstValue("Location"));
  }

  @Test
  void the_cookie_is_http_only_and_same_site_strict() throws Exception {
    var email = rand(12) + "@hei.test";
    account(email, "correct-horse", Role.ADMIN);

    var setCookie =
        postLogin(email, "correct-horse").headers().firstValue("Set-Cookie").orElseThrow();

    assertTrue(setCookie.startsWith("access_token="), "cookie was " + setCookie);
    assertTrue(setCookie.contains("HttpOnly"), "cookie was " + setCookie);
    assertTrue(setCookie.contains("SameSite=Strict"), "cookie was " + setCookie);
    assertTrue(setCookie.contains("Secure"), "cookie was " + setCookie);
  }

  @Test
  void a_wrong_password_re_renders_the_form_with_an_error_and_no_cookie() throws Exception {
    var email = rand(12) + "@hei.test";
    account(email, "correct-horse", Role.ADMIN);

    var response = postLogin(email, "wrong");

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<form"), "body was " + response.body());
    assertTrue(response.headers().firstValue("Set-Cookie").isEmpty(), "no cookie must be issued");
  }

  @Test
  void an_unknown_email_is_refused_the_same_way_as_a_wrong_password() throws Exception {
    var response = postLogin(rand(12) + "@hei.test", "whatever");

    assertEquals(200, response.statusCode());
    assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
  }
}
