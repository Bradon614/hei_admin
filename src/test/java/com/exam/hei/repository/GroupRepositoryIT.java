package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class GroupRepositoryIT extends FacadeIT {

  @Autowired GroupRepository groupRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TrackRepository trackRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Promotion promotionOpening(Track... tracks) {
    return promotionRepository.save(
        Promotion.builder()
            .ref(TestRefs.promotionRef())
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .tracks(List.of(tracks))
            .build());
  }

  private Track el() {
    return trackRepository.findByCode("EL").orElseThrow();
  }

  private Track tn() {
    return trackRepository.findByCode("TN").orElseThrow();
  }

  @Test
  void a_common_core_group_carries_no_track() {
    var saved =
        groupRepository.save(Group.builder().ref(rand(10)).promotion(promotionOpening()).build());

    assertNull(groupRepository.findById(saved.getId()).orElseThrow().getTrack());
  }

  @Test
  void a_group_carries_a_track_its_promotion_opens() {
    var promotion = promotionOpening(el());

    var saved =
        groupRepository.save(
            Group.builder().ref(rand(10)).promotion(promotion).track(el()).build());

    assertEquals(
        el().getId(), groupRepository.findById(saved.getId()).orElseThrow().getTrack().getId());
  }

  @Test
  void a_group_cannot_carry_a_track_its_promotion_does_not_open() {
    // Guarded by the composite foreign key to promotion_track.
    var promotionOpeningOnlyEl = promotionOpening(el());

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            groupRepository.saveAndFlush(
                Group.builder()
                    .ref(rand(10))
                    .promotion(promotionOpeningOnlyEl)
                    .track(tn())
                    .build()));
  }

  @Test
  void two_groups_of_a_promotion_cannot_share_a_ref() {
    var promotion = promotionOpening();
    var ref = rand(10);
    groupRepository.saveAndFlush(Group.builder().ref(ref).promotion(promotion).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> groupRepository.saveAndFlush(Group.builder().ref(ref).promotion(promotion).build()));
  }

  @Test
  void two_promotions_may_each_have_a_group_of_the_same_ref() {
    // Group refs are only unique within a promotion: two cohorts can each have their own "1".
    var ref = rand(10);
    groupRepository.saveAndFlush(Group.builder().ref(ref).promotion(promotionOpening()).build());

    assertDoesNotThrow(
        () ->
            groupRepository.saveAndFlush(
                Group.builder().ref(ref).promotion(promotionOpening()).build()));
  }

  @Test
  void groups_are_listed_by_promotion() {
    var promotion = promotionOpening();
    groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
    groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
    groupRepository.save(Group.builder().ref(rand(10)).promotion(promotionOpening()).build());

    assertEquals(2, groupRepository.findAllByPromotionIdOrderByRefAsc(promotion.getId()).size());
  }

  @Test
  void a_group_is_findable_by_its_promotion_and_ref() {
    var promotion = promotionOpening();
    var saved = groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());

    assertEquals(
        saved.getId(),
        groupRepository
            .findByPromotionIdAndRef(promotion.getId(), saved.getRef())
            .orElseThrow()
            .getId());
  }
}
