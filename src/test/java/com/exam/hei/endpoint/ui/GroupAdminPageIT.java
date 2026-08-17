package com.exam.hei.endpoint.ui;

import static java.net.http.HttpClient.Redirect.NEVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
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

class GroupAdminPageIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired GroupRepository groupRepository;
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

  /** Opens EL only, so TN is the track this promotion must refuse. */
  private Promotion promotionOpeningEl() {
    return promotionRepository.save(
        Promotion.builder()
            .ref(TestRefs.promotionRef())
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .tracks(List.of(trackRepository.findByCode("EL").orElseThrow()))
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

  private HttpResponse<String> post(Map<String, String> fields, String cookieToken)
      throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(restTemplate.getRootUri() + "/ui/admin/groups"))
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

  // --- creating ---------------------------------------------------------------------

  @Test
  void a_mixed_group_is_created_without_a_track() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpeningEl();
    var ref = rand(8);

    var response =
        post(
            Map.of("ref", ref, "track_id", "", "promotion_id", promotion.getId().toString()),
            admin);

    assertEquals(302, response.statusCode(), "body was " + response.body());
    var created =
        groupRepository.findAllByPromotionIdOrderByRefAsc(promotion.getId()).stream()
            .filter(g -> g.getRef().equals(ref))
            .findFirst()
            .orElseThrow();
    assertTrue(created.getTrack() == null, "a common core group carries no track");
  }

  @Test
  void a_group_can_carry_a_track_the_promotion_opens() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpeningEl();
    var el = trackRepository.findByCode("EL").orElseThrow();
    var ref = rand(8);

    var response =
        post(
            Map.of(
                "ref", ref,
                "track_id", el.getId().toString(),
                "promotion_id", promotion.getId().toString()),
            admin);

    assertEquals(302, response.statusCode(), "body was " + response.body());
  }

  @Test
  void a_track_the_promotion_does_not_open_is_refused_on_the_form() throws Exception {
    // The rule lives in GroupService; this checks the screen reports it rather than crashing.
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpeningEl();
    var tn = trackRepository.findByCode("TN").orElseThrow();

    var response =
        post(
            Map.of(
                "ref", rand(8),
                "track_id", tn.getId().toString(),
                "promotion_id", promotion.getId().toString()),
            admin);

    assertEquals(200, response.statusCode());
    assertTrue(
        response.body().contains("is not opened by promotion"), "body was " + response.body());
    assertTrue(response.body().contains("<form"), "the form must come back");
  }

  @Test
  void two_groups_of_one_promotion_cannot_share_a_reference() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpeningEl();
    var fields =
        Map.of("ref", rand(8), "track_id", "", "promotion_id", promotion.getId().toString());
    post(fields, admin);

    var again = post(fields, admin);

    assertEquals(200, again.statusCode());
    assertTrue(again.body().contains("already has a group"), "body was " + again.body());
  }

  // --- listing ------------------------------------------------------------------------

  @Test
  void the_new_group_appears_in_the_listing() throws Exception {
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpeningEl();
    var ref = rand(8);
    post(Map.of("ref", ref, "track_id", "", "promotion_id", promotion.getId().toString()), admin);

    var page = get("/ui/admin/groups?promotion_id=" + promotion.getId(), admin);

    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains(ref), "listing did not show the group");
  }

  @Test
  void the_form_only_offers_the_tracks_the_promotion_opens() throws Exception {
    // Offering TN here would invite a rejection the form could have avoided asking for.
    var admin = tokenFor(Role.ADMIN);
    var promotion = promotionOpeningEl();

    var body = get("/ui/admin/groups?promotion_id=" + promotion.getId(), admin).body();

    assertTrue(body.contains("EL —"), "EL should be offered");
    assertTrue(!body.contains("TN —"), "TN is not opened by this promotion");
  }

  // --- access --------------------------------------------------------------------------

  @Test
  void the_screen_is_closed_to_the_other_roles() throws Exception {
    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      assertEquals(403, get("/ui/admin/groups", tokenFor(role)).statusCode(), "role " + role);
    }
  }
}
