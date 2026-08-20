package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.StudentResult;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Track;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraduateServiceTest {
  private final ResultService resultService = mock(ResultService.class);
  private final StudentTrackChoiceService trackChoiceService =
      mock(StudentTrackChoiceService.class);
  private final GraduateService subject = new GraduateService(resultService, trackChoiceService);

  private static final UUID PROMOTION_ID = UUID.randomUUID();
  private static final Track EL = Track.builder().id(UUID.randomUUID()).code("EL").build();
  private static final Track TN = Track.builder().id(UUID.randomUUID()).code("TN").build();

  private static Student student(String ref, String lastName, String firstName) {
    return Student.builder()
        .id(UUID.randomUUID())
        .ref(ref)
        .lastName(lastName)
        .firstName(firstName)
        .build();
  }

  private static StudentResult resultOf(Student student, String average, boolean graduated) {
    return new StudentResult(
        student, List.of(), 180, new BigDecimal(average), graduated, List.of());
  }

  private void promotionHas(StudentResult... results) {
    when(resultService.resultsOfPromotion(PROMOTION_ID, null, 1, Pagination.MAX_PAGE_SIZE))
        .thenReturn(List.of(results));
  }

  @Test
  void graduates_are_ranked_by_descending_average() {
    var first = student("STD1", "Rakoto", "Jean");
    var second = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(second, "12.00", true), resultOf(first, "16.00", true));

    var ranked = subject.graduatesOf(PROMOTION_ID, null);

    assertEquals("STD1", ranked.get(0).std());
    assertEquals(1, ranked.get(0).rank());
    assertEquals("STD2", ranked.get(1).std());
    assertEquals(2, ranked.get(1).rank());
  }

  @Test
  void tied_students_share_the_same_rank_and_the_next_one_skips_ahead() {
    var first = student("STD1", "Andria", "Zo");
    var second = student("STD2", "Zafy", "Ary");
    var third = student("STD3", "Rakoto", "Jean");
    promotionHas(
        resultOf(third, "10.00", true),
        resultOf(first, "15.00", true),
        resultOf(second, "15.00", true));

    var ranked = subject.graduatesOf(PROMOTION_ID, null);

    assertEquals(1, ranked.get(0).rank());
    assertEquals(1, ranked.get(1).rank());
    assertEquals(3, ranked.get(2).rank());
  }

  @Test
  void ties_are_ordered_by_last_name_then_first_name() {
    var zafy = student("STD1", "Zafy", "Ary");
    var andria = student("STD2", "Andria", "Zo");
    promotionHas(resultOf(zafy, "15.00", true), resultOf(andria, "15.00", true));

    var ranked = subject.graduatesOf(PROMOTION_ID, null);

    assertEquals("Andria", ranked.get(0).lastName());
    assertEquals("Zafy", ranked.get(1).lastName());
  }

  @Test
  void a_non_graduated_student_is_excluded() {
    var graduated = student("STD1", "Rakoto", "Jean");
    var failed = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(graduated, "12.00", true), resultOf(failed, "8.00", false));

    var ranked = subject.graduatesOf(PROMOTION_ID, null);

    assertEquals(1, ranked.size());
    assertEquals("STD1", ranked.get(0).std());
  }

  @Test
  void a_graduate_carries_their_exit_track() {
    var student = student("STD1", "Rakoto", "Jean");
    promotionHas(resultOf(student, "14.00", true));
    when(trackChoiceService.exitTrackOf(student.getId())).thenReturn(Optional.of(EL));

    assertEquals(EL, subject.graduatesOf(PROMOTION_ID, null).get(0).track());
  }

  @Test
  void an_empty_promotion_yields_no_graduate() {
    promotionHas();

    assertTrue(subject.graduatesOf(PROMOTION_ID, null).isEmpty());
  }

  @Test
  void the_widest_page_is_requested_since_this_endpoint_is_never_paginated() {
    promotionHas();

    subject.graduatesOf(PROMOTION_ID, null);

    verify(resultService)
        .resultsOfPromotion(eq(PROMOTION_ID), any(), eq(1), eq(Pagination.MAX_PAGE_SIZE));
  }

  private void exitTracks(Student elStudent, Student tnStudent) {
    when(trackChoiceService.exitTrackOf(elStudent.getId())).thenReturn(Optional.of(EL));
    when(trackChoiceService.exitTrackOf(tnStudent.getId())).thenReturn(Optional.of(TN));
  }

  @Test
  void a_track_filter_keeps_only_the_graduates_who_left_on_that_track() {
    var el = student("STD1", "Rakoto", "Jean");
    var tn = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(el, "16.00", true), resultOf(tn, "18.00", true));
    exitTracks(el, tn);

    var filtered = subject.graduatesOf(PROMOTION_ID, "EL");

    assertEquals(1, filtered.size());
    assertEquals("STD1", filtered.get(0).std());
  }

  @Test
  void the_other_track_returns_the_other_graduate() {
    var el = student("STD1", "Rakoto", "Jean");
    var tn = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(el, "16.00", true), resultOf(tn, "18.00", true));
    exitTracks(el, tn);

    var filtered = subject.graduatesOf(PROMOTION_ID, "TN");

    assertEquals(1, filtered.size());
    assertEquals("STD2", filtered.get(0).std());
  }

  @Test
  void a_filtered_list_keeps_the_rank_held_in_the_whole_promotion() {
    var el = student("STD1", "Rakoto", "Jean");
    var tn = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(el, "16.00", true), resultOf(tn, "18.00", true));
    exitTracks(el, tn);

    var filtered = subject.graduatesOf(PROMOTION_ID, "EL");

    assertEquals(2, filtered.get(0).rank());
  }

  @Test
  void an_unknown_track_matches_nobody() {
    var el = student("STD1", "Rakoto", "Jean");
    promotionHas(resultOf(el, "16.00", true));
    when(trackChoiceService.exitTrackOf(el.getId())).thenReturn(Optional.of(EL));

    assertTrue(subject.graduatesOf(PROMOTION_ID, "XX").isEmpty());
  }

  @Test
  void a_graduate_with_no_exit_track_is_left_out_of_a_filtered_list() {
    var trackless = student("STD1", "Rakoto", "Jean");
    promotionHas(resultOf(trackless, "16.00", true));
    when(trackChoiceService.exitTrackOf(trackless.getId())).thenReturn(Optional.empty());

    assertTrue(subject.graduatesOf(PROMOTION_ID, "EL").isEmpty());
    assertEquals(1, subject.graduatesOf(PROMOTION_ID, null).size());
  }

  @Test
  void a_blank_filter_behaves_like_no_filter_at_all() {
    var el = student("STD1", "Rakoto", "Jean");
    var tn = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(el, "16.00", true), resultOf(tn, "18.00", true));
    exitTracks(el, tn);

    assertEquals(2, subject.graduatesOf(PROMOTION_ID, "").size());
  }
}
