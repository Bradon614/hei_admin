package com.exam.hei.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.endpoint.rest.model.Track;
import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.AppUserRepository;
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

class TrackIT extends FacadeIT {

  @Autowired TestRestTemplate restTemplate;
  @Autowired AppUserRepository appUserRepository;
  @Autowired JwtService jwtService;

  private String apiKeyOf(Role role) {
    var user =
        appUserRepository.save(
            AppUser.builder()
                .email(UUID.randomUUID() + "@hei.test")
                .passwordHash("hash")
                .role(role)
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

  private List<Track> getTracks(String apiKey) {
    return restTemplate
        .exchange(
            "/tracks",
            GET,
            new HttpEntity<>(bearer(apiKey)),
            new ParameterizedTypeReference<List<Track>>() {})
        .getBody();
  }

  @Test
  void listing_tracks_requires_authentication() {
    var response =
        restTemplate.exchange("/tracks", GET, new HttpEntity<>(bearer(null)), String.class);

    assertEquals(UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void the_two_hei_tracks_are_exposed() {
    // Filtered rather than counted: the Postgres container is shared, and other suites insert
    // throwaway tracks of their own.
    var codes = getTracks(apiKeyOf(Role.STUDENT)).stream().map(Track::getCode).toList();

    assertTrue(codes.contains("EL"), "tracks were " + codes);
    assertTrue(codes.contains("TN"), "tracks were " + codes);
  }

  @Test
  void a_listed_track_carries_its_name_and_id() {
    var el =
        getTracks(apiKeyOf(Role.ADMIN)).stream()
            .filter(t -> "EL".equals(t.getCode()))
            .findFirst()
            .orElseThrow();

    assertNotNull(el.getId());
    assertEquals("Software Ecosystem", el.getName());
  }

  @Test
  void only_an_admin_can_write_tracks() {
    var body =
        List.of(
            Track.builder()
                .code(UUID.randomUUID().toString().substring(0, 8))
                .name("New track")
                .build());

    assertEquals(
        FORBIDDEN,
        restTemplate
            .exchange(
                "/tracks",
                PUT,
                new HttpEntity<>(body, bearer(apiKeyOf(Role.TEACHER))),
                String.class)
            .getStatusCode());
  }

  @Test
  void an_admin_creates_a_track() {
    var code = UUID.randomUUID().toString().substring(0, 8);
    var body = List.of(Track.builder().code(code).name("New track").build());

    var created =
        restTemplate.exchange(
            "/tracks",
            PUT,
            new HttpEntity<>(body, bearer(apiKeyOf(Role.ADMIN))),
            new ParameterizedTypeReference<List<Track>>() {});

    assertEquals(OK, created.getStatusCode());
    assertNotNull(created.getBody().get(0).getId());
    assertEquals(code, created.getBody().get(0).getCode());
  }
}
