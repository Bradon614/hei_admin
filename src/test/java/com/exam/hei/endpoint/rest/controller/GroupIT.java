package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Group;
import com.exam.hei.endpoint.rest.model.Track;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class GroupIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired PromotionRepository promotionRepository;
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

  private com.exam.hei.repository.model.Track el() {
    return trackRepository.findByCode("EL").orElseThrow();
  }

  private com.exam.hei.repository.model.Track tn() {
    return trackRepository.findByCode("TN").orElseThrow();
  }

  private UUID promotionOpening(com.exam.hei.repository.model.Track... tracks) {
    return promotionRepository
        .save(
            Promotion.builder()
                .ref(rand(5))
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .tracks(List.of(tracks))
                .build())
        .getId();
  }

  private ResponseEntity<List<Group>> put(UUID promotionId, List<Group> body, String apiKey) {
    return restTemplate.exchange(
        "/promotions/" + promotionId + "/groups",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<String> putRaw(UUID promotionId, List<Group> body, String apiKey) {
    return restTemplate.exchange(
        "/promotions/" + promotionId + "/groups",
        PUT,
        new HttpEntity<>(body, bearer(apiKey)),
        String.class);
  }

  @Test
  void listing_groups_requires_authentication() {
    assertEquals(
        UNAUTHORIZED,
        restTemplate
            .exchange(
                "/promotions/" + UUID.randomUUID() + "/groups",
                GET,
                new HttpEntity<>(bearer(null)),
                String.class)
            .getStatusCode());
  }

  @Test
  void a_common_core_group_is_created_then_listed() {
    var admin = adminKey();
    var promotionId = promotionOpening();

    var created =
        put(promotionId, List.of(Group.builder().ref("K1").build()), admin).getBody().get(0);

    assertNotNull(created.getId());
    assertEquals("K1", created.getRef());
    assertNull(created.getTrack());
    assertEquals(promotionId, created.getPromotion().getId());

    var listed =
        restTemplate.exchange(
            "/promotions/" + promotionId + "/groups",
            GET,
            new HttpEntity<>(bearer(admin)),
            new ParameterizedTypeReference<List<Group>>() {});

    assertEquals(OK, listed.getStatusCode());
    assertEquals(1, listed.getBody().size());
  }

  @Test
  void a_group_carries_a_track_its_promotion_opens() {
    var promotionId = promotionOpening(el());

    var created =
        put(
                promotionId,
                List.of(
                    Group.builder()
                        .ref("K3-EL")
                        .track(Track.builder().id(el().getId()).build())
                        .build()),
                adminKey())
            .getBody()
            .get(0);

    assertEquals("EL", created.getTrack().getCode());
  }

  @Test
  void a_group_cannot_carry_a_track_its_promotion_does_not_open() {
    var promotionOpeningOnlyEl = promotionOpening(el());

    var response =
        putRaw(
            promotionOpeningOnlyEl,
            List.of(
                Group.builder()
                    .ref("K5-TN")
                    .track(Track.builder().id(tn().getId()).build())
                    .build()),
            adminKey());

    assertEquals(400, response.getStatusCode().value());
    assertTrue(
        response.getBody().contains("BadRequestException"), "body was " + response.getBody());
  }

  @Test
  void groups_of_an_unknown_promotion_are_not_found() {
    assertEquals(
        NOT_FOUND,
        restTemplate
            .exchange(
                "/promotions/" + UUID.randomUUID() + "/groups",
                GET,
                new HttpEntity<>(bearer(adminKey())),
                String.class)
            .getStatusCode());
  }

  @Test
  void a_group_is_updated_rather_than_duplicated() {
    var admin = adminKey();
    var promotionId = promotionOpening();
    var created =
        put(promotionId, List.of(Group.builder().ref("K1").build()), admin).getBody().get(0);

    created.setRef("K1-renamed");
    var updated = put(promotionId, List.of(created), admin).getBody().get(0);

    assertEquals(created.getId(), updated.getId());
    assertEquals("K1-renamed", updated.getRef());
  }

  @Test
  void only_an_admin_can_write_groups() {
    var promotionId = promotionOpening();

    for (var role : List.of(Role.STUDENT, Role.TEACHER)) {
      var user =
          appUserRepository.save(
              AppUser.builder()
                  .email(rand(12) + "@hei.test")
                  .passwordHash("hash")
                  .role(role)
                  .build());
      var apiKey = jwtService.issue(user).token();

      assertEquals(
          403,
          putRaw(promotionId, List.of(Group.builder().ref("K1").build()), apiKey)
              .getStatusCode()
              .value(),
          "role " + role);
    }
  }

  @Test
  void groups_are_serialized_in_snake_case() {
    var admin = adminKey();
    var promotionId = promotionOpening();
    put(promotionId, List.of(Group.builder().ref("K1").build()), admin);

    var raw =
        restTemplate
            .exchange(
                "/promotions/" + promotionId + "/groups",
                GET,
                new HttpEntity<>(bearer(admin)),
                String.class)
            .getBody();

    assertTrue(raw.contains("\"start_year\""), "body was " + raw);
  }
}
