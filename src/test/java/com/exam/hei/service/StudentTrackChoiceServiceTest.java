package com.exam.hei.service;

import static com.exam.hei.model.TrackResolution.Status.COMMON_CORE;
import static com.exam.hei.model.TrackResolution.Status.RESOLVED;
import static com.exam.hei.model.TrackResolution.Status.TRACK_NOT_SELECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.StudentTrackChoiceRepository;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentTrackChoice;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentTrackChoiceServiceTest {
  private final StudentTrackChoiceRepository trackChoiceRepository =
      mock(StudentTrackChoiceRepository.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final SemesterRepository semesterRepository = mock(SemesterRepository.class);
  private final TrackService trackService = mock(TrackService.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final StudentTrackChoiceService subject =
      new StudentTrackChoiceService(
          trackChoiceRepository,
          studentRepository,
          semesterRepository,
          trackService,
          studentAuthorizer);

  private static final UUID STUDENT_ID = UUID.randomUUID();

  private static final Track EL =
      Track.builder().id(UUID.randomUUID()).code("EL").name("Software Ecosystem").build();
  private static final Track TN =
      Track.builder().id(UUID.randomUUID()).code("TN").name("Digital Transformation").build();

  private static Semester semester(SemesterRef ref, int order, boolean commonCore) {
    return Semester.builder()
        .id(UUID.randomUUID())
        .ref(ref)
        .semOrder(order)
        .yearNumber((order + 1) / 2)
        .requiredCredits(30)
        .commonCore(commonCore)
        .build();
  }

  private static final Semester S1 = semester(SemesterRef.S1, 1, true);
  private static final Semester S3 = semester(SemesterRef.S3, 3, true);
  private static final Semester S4 = semester(SemesterRef.S4, 4, false);
  private static final Semester S5 = semester(SemesterRef.S5, 5, false);

  private void studentExistsOpening(Track... openedTracks) {
    when(studentRepository.findById(STUDENT_ID))
        .thenReturn(
            Optional.of(
                Student.builder()
                    .id(STUDENT_ID)
                    .promotion(Promotion.builder().ref("K").tracks(List.of(openedTracks)).build())
                    .build()));
  }

  private void tracksBeginAt(Semester semester) {
    when(semesterRepository.findFirstByCommonCoreFalseOrderBySemOrderAsc())
        .thenReturn(Optional.of(semester));
  }

  private void hasNoChoiceYet() {
    when(trackChoiceRepository.findAllByStudentIdOrderByFromSemesterSemOrderAsc(STUDENT_ID))
        .thenReturn(List.of());
  }

  @Test
  void a_common_core_semester_resolves_to_no_track_at_all() {
    var resolution = subject.resolve(STUDENT_ID, S1);

    assertEquals(COMMON_CORE, resolution.status());
    assertNull(resolution.track());
  }

  @Test
  void a_track_semester_resolves_to_the_ruling_choice() {
    when(trackChoiceRepository.findRulingChoice(STUDENT_ID, 4))
        .thenReturn(Optional.of(StudentTrackChoice.builder().track(EL).build()));

    var resolution = subject.resolve(STUDENT_ID, S4);

    assertEquals(RESOLVED, resolution.status());
    assertEquals(EL, resolution.track());
  }

  @Test
  void a_track_semester_without_a_choice_is_an_error_not_an_absence() {
    when(trackChoiceRepository.findRulingChoice(STUDENT_ID, 4)).thenReturn(Optional.empty());

    var resolution = subject.resolve(STUDENT_ID, S4);

    assertEquals(TRACK_NOT_SELECTED, resolution.status());
    assertTrue(resolution.isNotSelected());
  }

  @Test
  void a_common_core_semester_is_never_looked_up() {
    subject.resolve(STUDENT_ID, S3);

    org.mockito.Mockito.verify(trackChoiceRepository, org.mockito.Mockito.never())
        .findRulingChoice(any(), anyInt());
  }

  @Test
  void the_exit_track_is_the_most_recent_choice() {
    when(trackChoiceRepository.findFirstByStudentIdOrderByFromSemesterSemOrderDesc(STUDENT_ID))
        .thenReturn(Optional.of(StudentTrackChoice.builder().track(TN).build()));

    assertEquals(Optional.of(TN), subject.exitTrackOf(STUDENT_ID));
  }

  @Test
  void a_student_still_in_the_common_core_has_no_exit_track() {
    when(trackChoiceRepository.findFirstByStudentIdOrderByFromSemesterSemOrderDesc(STUDENT_ID))
        .thenReturn(Optional.empty());

    assertTrue(subject.exitTrackOf(STUDENT_ID).isEmpty());
  }

  @Test
  void a_first_choice_takes_effect_where_tracks_begin() {
    studentExistsOpening(EL);
    when(trackService.findById(EL.getId())).thenReturn(EL);
    when(semesterRepository.findByRef(SemesterRef.S4)).thenReturn(Optional.of(S4));
    tracksBeginAt(S4);
    hasNoChoiceYet();
    when(trackChoiceRepository.findByStudentIdAndFromSemesterId(STUDENT_ID, S4.getId()))
        .thenReturn(Optional.empty());
    when(trackChoiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var saved = subject.choose(STUDENT_ID, EL.getId(), SemesterRef.S4, "chose EL");

    assertEquals(EL, saved.getTrack());
    assertEquals(S4, saved.getFromSemester());
  }

  @Test
  void a_track_is_never_chosen_for_a_common_core_semester() {
    studentExistsOpening(EL);
    when(trackService.findById(EL.getId())).thenReturn(EL);
    when(semesterRepository.findByRef(SemesterRef.S1)).thenReturn(Optional.of(S1));

    assertThrows(
        BadRequestException.class,
        () -> subject.choose(STUDENT_ID, EL.getId(), SemesterRef.S1, null));
  }

  @Test
  void a_first_choice_cannot_start_after_tracks_begin() {
    studentExistsOpening(EL);
    when(trackService.findById(EL.getId())).thenReturn(EL);
    when(semesterRepository.findByRef(SemesterRef.S5)).thenReturn(Optional.of(S5));
    tracksBeginAt(S4);
    hasNoChoiceYet();

    assertThrows(
        BadRequestException.class,
        () -> subject.choose(STUDENT_ID, EL.getId(), SemesterRef.S5, null));
  }

  @Test
  void a_reorientation_may_start_later_than_the_first_choice() {
    studentExistsOpening(EL, TN);
    when(trackService.findById(TN.getId())).thenReturn(TN);
    when(semesterRepository.findByRef(SemesterRef.S5)).thenReturn(Optional.of(S5));
    when(trackChoiceRepository.findAllByStudentIdOrderByFromSemesterSemOrderAsc(STUDENT_ID))
        .thenReturn(List.of(StudentTrackChoice.builder().track(EL).fromSemester(S4).build()));
    when(trackChoiceRepository.findByStudentIdAndFromSemesterId(STUDENT_ID, S5.getId()))
        .thenReturn(Optional.empty());
    when(trackChoiceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var saved = subject.choose(STUDENT_ID, TN.getId(), SemesterRef.S5, "reorientation");

    assertEquals(TN, saved.getTrack());
  }

  @Test
  void a_track_the_promotion_does_not_open_is_refused() {
    studentExistsOpening(EL);
    when(trackService.findById(TN.getId())).thenReturn(TN);
    when(semesterRepository.findByRef(SemesterRef.S4)).thenReturn(Optional.of(S4));
    tracksBeginAt(S4);
    hasNoChoiceYet();

    assertThrows(
        BadRequestException.class,
        () -> subject.choose(STUDENT_ID, TN.getId(), SemesterRef.S4, null));
  }

  @Test
  void a_semester_already_covered_by_a_choice_is_a_conflict() {
    studentExistsOpening(EL);
    when(trackService.findById(EL.getId())).thenReturn(EL);
    when(semesterRepository.findByRef(SemesterRef.S4)).thenReturn(Optional.of(S4));
    tracksBeginAt(S4);
    hasNoChoiceYet();
    when(trackChoiceRepository.findByStudentIdAndFromSemesterId(STUDENT_ID, S4.getId()))
        .thenReturn(Optional.of(StudentTrackChoice.builder().build()));

    assertThrows(
        ConflictException.class,
        () -> subject.choose(STUDENT_ID, EL.getId(), SemesterRef.S4, null));
  }

  @Test
  void an_unknown_student_is_not_found() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> subject.choose(STUDENT_ID, EL.getId(), SemesterRef.S4, null));
  }

  @Test
  void an_unknown_track_is_not_found() {
    studentExistsOpening(EL);
    when(trackService.findById(EL.getId())).thenThrow(new NotFoundException("Track not found"));

    assertThrows(
        NotFoundException.class,
        () -> subject.choose(STUDENT_ID, EL.getId(), SemesterRef.S4, null));
  }

  @Test
  void reading_the_choices_of_a_student_is_authorized_first() {
    when(studentRepository.findById(STUDENT_ID))
        .thenReturn(Optional.of(Student.builder().id(STUDENT_ID).build()));
    when(trackChoiceRepository.findAllByStudentIdOrderByFromSemesterSemOrderAsc(STUDENT_ID))
        .thenReturn(List.of());

    subject.findAllByStudentId(STUDENT_ID);

    org.mockito.Mockito.verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void reading_the_choices_of_an_unknown_student_is_not_found() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findAllByStudentId(STUDENT_ID));
  }
}
