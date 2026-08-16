package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class CourseServiceTest {
  private final CourseRepository courseRepository = mock(CourseRepository.class);
  private final SemesterRepository semesterRepository = mock(SemesterRepository.class);
  private final TrackService trackService = mock(TrackService.class);
  private final CourseService subject =
      new CourseService(courseRepository, semesterRepository, trackService);

  private static final Track EL =
      Track.builder().id(UUID.randomUUID()).code("EL").name("Software Ecosystem").build();

  private static Semester semester(SemesterRef ref, int order, boolean commonCore) {
    return Semester.builder()
        .id(UUID.randomUUID())
        .ref(ref)
        .semOrder(order)
        .requiredCredits(30)
        .commonCore(commonCore)
        .build();
  }

  private static final Semester S1 = semester(SemesterRef.S1, 1, true);
  private static final Semester S5 = semester(SemesterRef.S5, 5, false);

  private void semesterExists(Semester semester) {
    when(semesterRepository.findByRef(semester.getRef())).thenReturn(Optional.of(semester));
    when(semesterRepository.findById(semester.getId())).thenReturn(Optional.of(semester));
  }

  private void repositoryEchoesWhatItIsGiven() {
    when(courseRepository.saveAll(any()))
        .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
  }

  private static Course course(Semester semester, Track track, int credits) {
    return Course.builder()
        .ref("PROG1")
        .title("Course under test")
        .credits(credits)
        .semester(Semester.builder().ref(semester.getRef()).build())
        .track(track == null ? null : Track.builder().id(track.getId()).build())
        .build();
  }

  @Test
  void a_common_core_course_carries_no_track() {
    semesterExists(S1);
    repositoryEchoesWhatItIsGiven();

    assertNull(subject.saveAll(List.of(course(S1, null, 6))).get(0).getTrack());
  }

  @Test
  void a_common_core_semester_refuses_a_track_specific_course() {
    semesterExists(S1);

    var thrown =
        assertThrows(BadRequestException.class, () -> subject.saveAll(List.of(course(S1, EL, 6))));

    org.junit.jupiter.api.Assertions.assertTrue(
        thrown.getMessage().contains("common core"), thrown.getMessage());
  }

  @Test
  void a_track_semester_accepts_a_track_specific_course() {
    semesterExists(S5);
    when(trackService.findById(EL.getId())).thenReturn(EL);
    repositoryEchoesWhatItIsGiven();

    assertEquals(EL, subject.saveAll(List.of(course(S5, EL, 18))).get(0).getTrack());
  }

  @Test
  void a_track_semester_also_accepts_a_common_course() {
    semesterExists(S5);
    repositoryEchoesWhatItIsGiven();

    assertNull(subject.saveAll(List.of(course(S5, null, 12))).get(0).getTrack());
  }

  @Test
  void a_course_names_the_semester_it_belongs_to() {
    var orphan = course(S1, null, 6);
    orphan.setSemester(null);

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(orphan)));
  }

  @Test
  void a_course_naming_an_unknown_semester_is_not_found() {
    when(semesterRepository.findByRef(SemesterRef.S1)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(course(S1, null, 6))));
  }

  @Test
  void a_semester_may_be_named_by_its_id_when_no_reference_is_given() {
    semesterExists(S5);
    repositoryEchoesWhatItIsGiven();
    var byId = course(S5, null, 12);
    byId.setSemester(Semester.builder().id(S5.getId()).build());

    assertEquals(S5, subject.saveAll(List.of(byId)).get(0).getSemester());
  }

  @Test
  void a_course_naming_an_unknown_track_is_not_found() {
    semesterExists(S5);
    when(trackService.findById(EL.getId())).thenThrow(new NotFoundException("Track not found"));

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(course(S5, EL, 18))));
  }

  @Test
  void a_course_naming_an_unknown_id_is_not_found() {
    var id = UUID.randomUUID();
    when(courseRepository.findById(id)).thenReturn(Optional.empty());
    var existing = course(S1, null, 6);
    existing.setId(id);

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(existing)));
  }

  @Test
  void a_course_is_worth_at_least_one_credit() {
    semesterExists(S1);

    assertThrows(BadRequestException.class, () -> subject.saveAll(List.of(course(S1, null, 0))));
  }

  @Test
  void an_unknown_course_is_not_found() {
    var id = UUID.randomUUID();
    when(courseRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void listing_without_a_filter_returns_every_course() {
    when(courseRepository.findAll(any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, null, null);

    verify(courseRepository).findAll(any(Pageable.class));
  }

  @Test
  void listing_by_semester_narrows_the_query() {
    when(courseRepository.findAllBySemesterRef(eq(SemesterRef.S5), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, SemesterRef.S5, null);

    verify(courseRepository).findAllBySemesterRef(eq(SemesterRef.S5), any(Pageable.class));
  }

  @Test
  void listing_by_track_narrows_the_query() {
    when(courseRepository.findAllFollowedByTrack(eq("EL"), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, null, "EL");

    verify(courseRepository).findAllFollowedByTrack(eq("EL"), any(Pageable.class));
  }

  @Test
  void both_filters_apply_together() {
    when(courseRepository.findAllOfSemesterFollowedByTrackCode(
            eq(SemesterRef.S5), eq("EL"), any(Pageable.class)))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.findAll(1, 50, SemesterRef.S5, "EL");

    verify(courseRepository)
        .findAllOfSemesterFollowedByTrackCode(eq(SemesterRef.S5), eq("EL"), any(Pageable.class));
    verify(courseRepository, never()).findAllBySemesterRef(any(), any());
  }

  @Test
  void an_invalid_page_is_a_bad_request() {
    assertThrows(BadRequestException.class, () -> subject.findAll(0, 50, null, null));
    assertThrows(BadRequestException.class, () -> subject.findAll(1, 501, null, null));
  }
}
