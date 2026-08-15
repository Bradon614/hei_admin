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

    var ranked = subject.graduatesOf(PROMOTION_ID);

    assertEquals("STD1", ranked.get(0).std());
    assertEquals(1, ranked.get(0).rank());
    assertEquals("STD2", ranked.get(1).std());
    assertEquals(2, ranked.get(1).rank());
  }

  @Test
  void tied_students_share_the_same_rank_and_the_next_one_skips_ahead() {
    // Two tied at rank 1, the third is rank 3, not 2: a competition ranking, not a dense one.
    var first = student("STD1", "Andria", "Zo");
    var second = student("STD2", "Zafy", "Ary");
    var third = student("STD3", "Rakoto", "Jean");
    promotionHas(
        resultOf(third, "10.00", true),
        resultOf(first, "15.00", true),
        resultOf(second, "15.00", true));

    var ranked = subject.graduatesOf(PROMOTION_ID);

    assertEquals(1, ranked.get(0).rank());
    assertEquals(1, ranked.get(1).rank());
    assertEquals(3, ranked.get(2).rank());
  }

  @Test
  void ties_are_ordered_by_last_name_then_first_name() {
    var zafy = student("STD1", "Zafy", "Ary");
    var andria = student("STD2", "Andria", "Zo");
    promotionHas(resultOf(zafy, "15.00", true), resultOf(andria, "15.00", true));

    var ranked = subject.graduatesOf(PROMOTION_ID);

    assertEquals("Andria", ranked.get(0).lastName());
    assertEquals("Zafy", ranked.get(1).lastName());
  }

  @Test
  void a_non_graduated_student_is_excluded() {
    var graduated = student("STD1", "Rakoto", "Jean");
    var failed = student("STD2", "Randria", "Aina");
    promotionHas(resultOf(graduated, "12.00", true), resultOf(failed, "8.00", false));

    var ranked = subject.graduatesOf(PROMOTION_ID);

    assertEquals(1, ranked.size());
    assertEquals("STD1", ranked.get(0).std());
  }

  @Test
  void a_graduate_carries_their_exit_track() {
    var student = student("STD1", "Rakoto", "Jean");
    promotionHas(resultOf(student, "14.00", true));
    when(trackChoiceService.exitTrackOf(student.getId())).thenReturn(Optional.of(EL));

    assertEquals(EL, subject.graduatesOf(PROMOTION_ID).get(0).track());
  }

  @Test
  void an_empty_promotion_yields_no_graduate() {
    promotionHas();

    assertTrue(subject.graduatesOf(PROMOTION_ID).isEmpty());
  }

  @Test
  void the_widest_page_is_requested_since_this_endpoint_is_never_paginated() {
    promotionHas();

    subject.graduatesOf(PROMOTION_ID);

    verify(resultService)
        .resultsOfPromotion(eq(PROMOTION_ID), any(), eq(1), eq(Pagination.MAX_PAGE_SIZE));
  }
}
