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
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.service.StudentTrackChoiceService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class PromotionResultPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired StudentTrackChoiceService trackChoiceService;
  @Autowired JwtService jwtService;

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

  private HttpResponse<String> get(String path, String cookieToken) throws Exception {
    var builder = HttpRequest.newBuilder().uri(URI.create(restTemplate.getRootUri() + path));
    if (cookieToken != null) {
      builder.header("Cookie", BearerAuthFilter.COOKIE_NAME + "=" + cookieToken);
    }
    return HTTP_CLIENT.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private Promotion promotionOpening(String... trackCodes) {
    var tracks =
        List.of(trackCodes).stream()
            .map(code -> trackRepository.findByCode(code).orElseThrow())
            .toList();
    return promotionRepository.save(
        Promotion.builder()
            .ref(TestRefs.promotionRef())
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .tracks(tracks)
            .build());
  }

  private Student studentOf(Promotion promotion) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(18))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(user)
            .build());
  }

  // --- what the page says -------------------------------------------------------------------

  @Test
  void a_student_with_no_grade_is_shown_as_not_graduated() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpening();
    var student = studentOf(promotion);

    var page = get("/ui/admin/promotions/" + promotion.getId() + "/results", admin);

    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains(student.getRef()), "the student should be listed");
    assertTrue(page.body().contains("NOT GRANTED"));
  }

  @Test
  void the_blockers_say_what_stands_in_the_way() throws Exception {
    // The distinction the model rests on: a semester with no track chosen is not a failed one.
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpening();
    studentOf(promotion);

    var body = get("/ui/admin/promotions/" + promotion.getId() + "/results", admin).body();

    assertTrue(body.contains("No track selected for S4"), "the S4 blocker is missing");
    assertTrue(body.contains("S1 not validated"), "a common core semester blocks on credits");
  }

  // --- the track filter -----------------------------------------------------------------------

  @Test
  void the_track_filter_keeps_only_the_students_who_chose_it() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpening("EL", "TN");
    var student = studentOf(promotion);
    trackChoiceService.choose(
        student.getId(),
        trackRepository.findByCode("EL").orElseThrow().getId(),
        SemesterRef.S4,
        "Chose the software ecosystem");

    var onEl = get("/ui/admin/promotions/" + promotion.getId() + "/results?track_code=EL", admin);
    var onTn = get("/ui/admin/promotions/" + promotion.getId() + "/results?track_code=TN", admin);

    assertTrue(onEl.body().contains(student.getRef()), "the EL listing should hold the student");
    assertTrue(!onTn.body().contains(student.getRef()), "the TN listing should not");
  }

  @Test
  void an_empty_filter_falls_back_to_the_whole_promotion() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpening();
    var student = studentOf(promotion);

    var page = get("/ui/admin/promotions/" + promotion.getId() + "/results?track_code=", admin);

    assertTrue(page.body().contains(student.getRef()));
  }

  // --- failures and access ---------------------------------------------------------------------

  @Test
  void an_unknown_promotion_is_reported_rather_than_thrown() throws Exception {
    var response =
        get("/ui/admin/promotions/" + UUID.randomUUID() + "/results", tokenFor(Role.ADMIN));

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("not found"), "body was " + response.body());
  }

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    var promotion = promotionOpening();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(
          403,
          get("/ui/admin/promotions/" + promotion.getId() + "/results", tokenFor(role))
              .statusCode(),
          "role " + role);
    }
  }
}
