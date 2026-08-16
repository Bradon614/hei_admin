package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class PromotionServiceTest {
  private final PromotionRepository promotionRepository = mock(PromotionRepository.class);
  private final TrackService trackService = mock(TrackService.class);
  private final PromotionService subject = new PromotionService(promotionRepository, trackService);

  private static Promotion promotion(UUID id) {
    return Promotion.builder()
        .id(id)
        .ref("K")
        .name("Promotion K")
        .startYear(2025)
        .endYear(2028)
        .tracks(new ArrayList<>())
        .build();
  }

  @Test
  void the_first_page_of_the_api_is_the_first_page_of_the_repository() {
    when(promotionRepository.findAll(any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50);

    var captured = org.mockito.ArgumentCaptor.forClass(Pageable.class);
    org.mockito.Mockito.verify(promotionRepository).findAll(captured.capture());
    assertEquals(0, captured.getValue().getPageNumber());
  }

  @Test
  void a_page_below_one_is_rejected() {
    assertThrows(BadRequestException.class, () -> subject.findAll(0, 50));
  }

  @Test
  void a_page_size_below_one_is_rejected() {
    assertThrows(BadRequestException.class, () -> subject.findAll(1, 0));
  }

  @Test
  void a_page_size_above_the_maximum_is_rejected() {
    assertThrows(BadRequestException.class, () -> subject.findAll(1, 501));
  }

  @Test
  void an_unknown_promotion_is_not_found() {
    var id = UUID.randomUUID();
    when(promotionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void a_promotion_without_an_id_is_created() {
    var toCreate = promotion(null);
    when(promotionRepository.saveAll(any())).thenReturn(List.of(toCreate));

    assertEquals(1, subject.saveAll(List.of(toCreate)).size());
  }

  @Test
  void a_promotion_naming_an_unknown_id_is_not_found() {
    var id = UUID.randomUUID();
    when(promotionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(promotion(id))));
  }

  @Test
  void the_named_tracks_are_replaced_by_the_persisted_ones() {
    var trackId = UUID.randomUUID();
    var persisted = Track.builder().id(trackId).code("EL").name("Software Ecosystem").build();
    when(trackService.findById(trackId)).thenReturn(persisted);

    var toSave = promotion(null);
    toSave.setTracks(new ArrayList<>(List.of(Track.builder().id(trackId).build())));
    when(promotionRepository.saveAll(any())).thenReturn(List.of(toSave));

    subject.saveAll(List.of(toSave));

    assertEquals(List.of(persisted), toSave.getTracks());
  }

  @Test
  void a_promotion_naming_an_unknown_track_is_not_found() {
    var trackId = UUID.randomUUID();
    when(trackService.findById(trackId)).thenThrow(new NotFoundException("Track not found"));

    var toSave = promotion(null);
    toSave.setTracks(new ArrayList<>(List.of(Track.builder().id(trackId).build())));

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(toSave)));
  }

  @Test
  void a_promotion_without_any_track_is_accepted() {
    var toSave = promotion(null);
    toSave.setTracks(null);
    when(promotionRepository.saveAll(any())).thenReturn(List.of(toSave));

    subject.saveAll(List.of(toSave));

    assertTrue(toSave.getTracks().isEmpty());
  }
}
