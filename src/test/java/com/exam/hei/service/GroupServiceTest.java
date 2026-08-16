package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupServiceTest {
  private final GroupRepository groupRepository = mock(GroupRepository.class);
  private final PromotionService promotionService = mock(PromotionService.class);
  private final TrackService trackService = mock(TrackService.class);
  private final GroupService subject =
      new GroupService(groupRepository, promotionService, trackService);

  private static final UUID PROMOTION_ID = UUID.randomUUID();

  private static final Track EL =
      Track.builder().id(UUID.randomUUID()).code("EL").name("Software Ecosystem").build();
  private static final Track TN =
      Track.builder().id(UUID.randomUUID()).code("TN").name("Digital Transformation").build();

  private void promotionOpens(Track... tracks) {
    when(promotionService.findById(PROMOTION_ID))
        .thenReturn(Promotion.builder().id(PROMOTION_ID).ref("K").tracks(List.of(tracks)).build());
  }

  private void repositoryEchoesWhatItIsGiven() {
    when(groupRepository.saveAll(any()))
        .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
  }

  @Test
  void a_group_is_attached_to_the_promotion_of_the_path() {
    promotionOpens();
    repositoryEchoesWhatItIsGiven();

    var saved = subject.saveAll(PROMOTION_ID, List.of(Group.builder().ref("K1").build())).get(0);

    assertEquals(PROMOTION_ID, saved.getPromotion().getId());
  }

  @Test
  void a_group_without_a_track_is_a_common_core_group() {
    promotionOpens();
    repositoryEchoesWhatItIsGiven();

    assertNull(
        subject
            .saveAll(PROMOTION_ID, List.of(Group.builder().ref("K1").build()))
            .get(0)
            .getTrack());
  }

  @Test
  void a_group_carries_a_track_its_promotion_opens() {
    promotionOpens(EL);
    when(trackService.findById(EL.getId())).thenReturn(EL);
    repositoryEchoesWhatItIsGiven();

    var saved =
        subject
            .saveAll(
                PROMOTION_ID,
                List.of(
                    Group.builder()
                        .ref("K3-EL")
                        .track(Track.builder().id(EL.getId()).build())
                        .build()))
            .get(0);

    assertEquals(EL, saved.getTrack());
  }

  @Test
  void a_group_cannot_carry_a_track_its_promotion_does_not_open() {
    promotionOpens(EL);
    when(trackService.findById(TN.getId())).thenReturn(TN);

    assertThrows(
        BadRequestException.class,
        () ->
            subject.saveAll(
                PROMOTION_ID,
                List.of(
                    Group.builder()
                        .ref("K5-TN")
                        .track(Track.builder().id(TN.getId()).build())
                        .build())));
  }

  @Test
  void a_group_naming_an_unknown_id_is_not_found() {
    promotionOpens();
    var unknown = UUID.randomUUID();
    when(groupRepository.findById(unknown)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () ->
            subject.saveAll(PROMOTION_ID, List.of(Group.builder().id(unknown).ref("K1").build())));
  }

  @Test
  void an_unknown_group_is_not_found() {
    var unknown = UUID.randomUUID();
    when(groupRepository.findById(unknown)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(unknown));
  }

  @Test
  void listing_the_groups_of_an_unknown_promotion_is_not_found() {
    when(promotionService.findById(PROMOTION_ID))
        .thenThrow(new NotFoundException("Promotion not found"));

    assertThrows(NotFoundException.class, () -> subject.findAllByPromotionId(PROMOTION_ID));
  }
}
