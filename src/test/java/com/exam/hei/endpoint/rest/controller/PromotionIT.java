package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Promotion;
import com.exam.hei.endpoint.rest.model.Track;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class PromotionIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired TrackRepository trackRepository;

  private String apiKeyOf(Role role) {
    var apiKey = UUID.randomUUID().toString();
    appUserRepository.save(
        AppUser.builder()
            .email(UUID.randomUUID() + "@hei.test")
            .passwordHash("hash")
            .role(role)
            .apiKey(apiKey)
            .build());
    return apiKey;
  }

  private static HttpHeaders bearer(String apiKey) {
    var headers = new HttpHeaders();
    if (apiKey != null) {
      headers.set(AUTHORIZATION, "Bearer " + apiKey);
    }
    return headers;
  }

  private static Promotion aPromotion() {
    // ref is a varchar(5) and unique across the shared test container.
    return Promotion.builder()
        .ref(UUID.randomUUID().toString().substring(0, 5))
        .name("Promotion under test")
        .startYear(2025)
        .endYear(2028)
        .build();
  }

  /** Typed variant, for calls expected to succeed. */
  private ResponseEntity<List<Promotion>> put(List<Promotion> body, String apiKey) {
    return restTemplate.exchange(
        "/promotions",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        new ParameterizedTypeReference<>() {});
  }

  /**
   * Raw variant, for calls expected to fail: an error response carries the Error object, which
   * cannot be read into a list of promotions.
   */
  private ResponseEntity<String> putRaw(List<Promotion> body, String apiKey) {
    return restTemplate.exchange(
        "/promotions", PUT, new HttpEntity<>(body, bearer(apiKey)), String.class);
  }

  private Promotion createdBy(String adminKey, Promotion promotion) {
    var created = put(List.of(promotion), adminKey).getBody();
    return created.get(0);
  }

  // --- authorization --------------------------------------------------------

  @Test
  void listing_promotions_requires_authentication() {
    var response =
        restTemplate.exchange("/promotions", GET, new HttpEntity<>(bearer(null)), String.class);

    assertEquals(UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void any_authenticated_role_can_list_promotions() {
    for (var role : List.of(Role.STUDENT, Role.TEACHER, Role.ADMIN)) {
      var response =
          restTemplate.exchange(
              "/promotions", GET, new HttpEntity<>(bearer(apiKeyOf(role))), String.class);

      assertEquals(OK, response.getStatusCode(), "role " + role);
    }
  }

  @Test
  void only_an_admin_can_write_promotions() {
    assertEquals(FORBIDDEN, putRaw(List.of(aPromotion()), apiKeyOf(Role.STUDENT)).getStatusCode());
    assertEquals(FORBIDDEN, putRaw(List.of(aPromotion()), apiKeyOf(Role.TEACHER)).getStatusCode());
    assertEquals(OK, putRaw(List.of(aPromotion()), apiKeyOf(Role.ADMIN)).getStatusCode());
  }

  @Test
  void a_forbidden_write_is_rendered_as_the_spec_error_payload() {
    var response =
        restTemplate.exchange(
            "/promotions",
            PUT,
            new HttpEntity<>(List.of(aPromotion()), bearer(apiKeyOf(Role.STUDENT))),
            String.class);

    assertEquals(FORBIDDEN, response.getStatusCode());
    assertTrue(response.getBody().contains("\"type\""), "body was " + response.getBody());
  }

  // --- crupdate then read ---------------------------------------------------

  @Test
  void a_promotion_is_created_then_readable_by_its_id() {
    var admin = apiKeyOf(Role.ADMIN);
    var created = createdBy(admin, aPromotion());

    assertNotNull(created.getId());

    var found =
        restTemplate.exchange(
            "/promotions/" + created.getId(),
            GET,
            new HttpEntity<>(bearer(admin)),
            Promotion.class);

    assertEquals(OK, found.getStatusCode());
    assertEquals(created.getRef(), found.getBody().getRef());
    assertEquals(2025, found.getBody().getStartYear());
    assertEquals(2028, found.getBody().getEndYear());
  }

  @Test
  void a_promotion_is_updated_rather_than_duplicated() {
    var admin = apiKeyOf(Role.ADMIN);
    var created = createdBy(admin, aPromotion());
    created.setName("Renamed promotion");

    var updated = put(List.of(created), admin).getBody().get(0);

    assertEquals(created.getId(), updated.getId());
    assertEquals("Renamed promotion", updated.getName());
  }

  @Test
  void an_unknown_promotion_id_is_not_found() {
    var response =
        restTemplate.exchange(
            "/promotions/" + UUID.randomUUID(),
            GET,
            new HttpEntity<>(bearer(apiKeyOf(Role.ADMIN))),
            String.class);

    assertEquals(NOT_FOUND, response.getStatusCode());
    assertTrue(response.getBody().contains("NotFoundException"), "body was " + response.getBody());
  }

  @Test
  void writing_a_promotion_at_an_unknown_id_is_not_found() {
    // Decided convention: an unknown id never creates a promotion at that id.
    var ghost = aPromotion();
    ghost.setId(UUID.randomUUID());

    var response =
        restTemplate.exchange(
            "/promotions",
            PUT,
            new HttpEntity<>(List.of(ghost), bearer(apiKeyOf(Role.ADMIN))),
            String.class);

    assertEquals(NOT_FOUND, response.getStatusCode());
  }

  // --- tracks opened by a promotion ----------------------------------------

  @Test
  void the_tracks_opened_by_a_promotion_are_persisted() {
    var admin = apiKeyOf(Role.ADMIN);
    var el = trackRepository.findByCode("EL").orElseThrow();
    var tn = trackRepository.findByCode("TN").orElseThrow();

    var promotion = aPromotion();
    promotion.setTracks(
        List.of(Track.builder().id(el.getId()).build(), Track.builder().id(tn.getId()).build()));

    var created = createdBy(admin, promotion);

    assertEquals(2, created.getTracks().size());
    var reread =
        restTemplate
            .exchange(
                "/promotions/" + created.getId(),
                GET,
                new HttpEntity<>(bearer(admin)),
                Promotion.class)
            .getBody();
    assertTrue(reread.getTracks().stream().anyMatch(t -> "EL".equals(t.getCode())));
    assertTrue(reread.getTracks().stream().anyMatch(t -> "TN".equals(t.getCode())));
  }

  @Test
  void a_promotion_naming_an_unknown_track_is_not_found() {
    var promotion = aPromotion();
    promotion.setTracks(List.of(Track.builder().id(UUID.randomUUID()).build()));

    var response =
        restTemplate.exchange(
            "/promotions",
            PUT,
            new HttpEntity<>(List.of(promotion), bearer(apiKeyOf(Role.ADMIN))),
            String.class);

    assertEquals(NOT_FOUND, response.getStatusCode());
  }

  // --- pagination -----------------------------------------------------------

  @Test
  void an_invalid_page_is_a_bad_request() {
    var response =
        restTemplate.exchange(
            "/promotions?page=0",
            HttpMethod.GET,
            new HttpEntity<>(bearer(apiKeyOf(Role.ADMIN))),
            String.class);

    assertEquals(400, response.getStatusCode().value());
    assertTrue(
        response.getBody().contains("BadRequestException"), "body was " + response.getBody());
  }

  @Test
  void page_size_limits_the_number_of_returned_promotions() {
    var admin = apiKeyOf(Role.ADMIN);
    put(List.of(aPromotion(), aPromotion()), admin);

    var page =
        restTemplate.exchange(
            "/promotions?page=1&page_size=1",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Promotion>>() {});

    assertEquals(1, page.getBody().size());
  }

  @Test
  void promotions_are_serialized_in_snake_case() {
    var admin = apiKeyOf(Role.ADMIN);
    var created = createdBy(admin, aPromotion());

    var raw =
        restTemplate
            .exchange(
                "/promotions/" + created.getId(),
                GET,
                new HttpEntity<>(bearer(admin)),
                String.class)
            .getBody();

    assertTrue(raw.contains("\"start_year\""), "body was " + raw);
    assertTrue(raw.contains("\"end_year\""), "body was " + raw);
  }
}
