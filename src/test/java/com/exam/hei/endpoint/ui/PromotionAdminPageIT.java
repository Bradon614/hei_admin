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
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class PromotionAdminPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TrackRepository trackRepository;
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

  private static String form(List<Map.Entry<String, String>> fields) {
    return fields.stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .reduce((a, b) -> a + "&" + b)
        .orElse("");
  }

  private HttpResponse<String> post(
      String path, List<Map.Entry<String, String>> fields, String cookieToken) throws Exception {
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

  private List<Map.Entry<String, String>> newPromotion(String ref, String... trackIds) {
    var fields = new ArrayList<Map.Entry<String, String>>();
    fields.add(Map.entry("ref", ref));
    fields.add(Map.entry("name", "Promotion under test"));
    fields.add(Map.entry("start_year", "2025"));
    fields.add(Map.entry("end_year", "2028"));
    for (var trackId : trackIds) {
      fields.add(Map.entry("track_ids", trackId));
    }
    return fields;
  }

  private String trackId(String code) {
    return trackRepository.findByCode(code).orElseThrow().getId().toString();
  }

  @Test
  void a_promotion_created_from_the_form_is_persisted_with_its_tracks() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = TestRefs.promotionRef();

    var created = post("/ui/admin/promotions", newPromotion(ref, trackId("EL")), admin);

    assertEquals(302, created.statusCode(), "body was " + created.body());
    var promotion = promotionRepository.findByRef(ref).orElseThrow();
    assertEquals(1, promotion.getTracks().size());
    assertEquals("EL", promotion.getTracks().get(0).getCode());
  }

  @Test
  void both_tracks_can_be_opened_at_once() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = TestRefs.promotionRef();

    post("/ui/admin/promotions", newPromotion(ref, trackId("EL"), trackId("TN")), admin);

    assertEquals(2, promotionRepository.findByRef(ref).orElseThrow().getTracks().size());
  }

  @Test
  void a_promotion_with_no_track_ticked_stays_common_core_only() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = TestRefs.promotionRef();

    var created = post("/ui/admin/promotions", newPromotion(ref), admin);

    assertEquals(302, created.statusCode(), "body was " + created.body());
    assertTrue(promotionRepository.findByRef(ref).orElseThrow().getTracks().isEmpty());
  }

  @Test
  void the_tracks_it_opens_are_the_ones_offered_when_creating_a_group() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = TestRefs.promotionRef();
    post("/ui/admin/promotions", newPromotion(ref, trackId("TN")), admin);
    var promotion = promotionRepository.findByRef(ref).orElseThrow();

    var groups = get("/ui/admin/groups?promotion_id=" + promotion.getId(), admin);

    assertEquals(200, groups.statusCode());
    assertTrue(
        groups.body().contains("TN — Digital Transformation"), "the opened track is missing");
    assertTrue(
        !groups.body().contains("EL — Software Ecosystem"),
        "a track this promotion does not open must not be offered");
  }

  @Test
  void a_duplicate_reference_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var ref = TestRefs.promotionRef();
    post("/ui/admin/promotions", newPromotion(ref), admin);

    var again = post("/ui/admin/promotions", newPromotion(ref), admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("<form"), "the form must come back");
    assertTrue(!again.body().startsWith("{"), "body was " + again.body());
  }

  @Test
  void a_reference_over_five_characters_is_reported_on_the_form() throws Exception {
    var admin = tokenFor(Role.ADMIN);

    var response = post("/ui/admin/promotions", newPromotion("TOOLONG"), admin);

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("at most 5 characters"), "body was " + response.body());
  }

  @Test
  void the_listing_is_served_with_its_form_to_an_administrator() throws Exception {
    var page = get("/ui/admin/promotions", tokenFor(Role.ADMIN));

    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("Tracks this promotion opens"));
    assertTrue(page.body().contains("EL — Software Ecosystem"), "every track should be tickable");
  }

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(403, get("/ui/admin/promotions", tokenFor(role)).statusCode(), "role " + role);
    }
  }
}
