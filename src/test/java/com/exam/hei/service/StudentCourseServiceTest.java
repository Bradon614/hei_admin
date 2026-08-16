package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.TrackResolution;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentCourseServiceTest {
  private final CourseRepository courseRepository = mock(CourseRepository.class);
  private final SemesterRepository semesterRepository = mock(SemesterRepository.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final StudentTrackChoiceService trackChoiceService =
      mock(StudentTrackChoiceService.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final StudentCourseService subject =
      new StudentCourseService(
          courseRepository,
          semesterRepository,
          studentRepository,
          trackChoiceService,
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
        .requiredCredits(30)
        .commonCore(commonCore)
        .build();
  }

  private static final Semester S2 = semester(SemesterRef.S2, 2, true);
  private static final Semester S5 = semester(SemesterRef.S5, 5, false);

  private static final Course COMMON_S5 = course("MATH5", 12, null);
  private static final Course EL_ONLY = course("PROG_AV", 18, EL);
  private static final Course TN_ONLY = course("TRANSFO", 18, TN);

  private static Course course(String ref, int credits, Track track) {
    return Course.builder().id(UUID.randomUUID()).ref(ref).credits(credits).track(track).build();
  }

  private void studentExists() {
    when(studentRepository.existsById(STUDENT_ID)).thenReturn(true);
    when(semesterRepository.findByRef(S2.getRef())).thenReturn(Optional.of(S2));
    when(semesterRepository.findByRef(S5.getRef())).thenReturn(Optional.of(S5));
  }

  private void followsInS5(Track track) {
    when(trackChoiceService.resolve(STUDENT_ID, S5)).thenReturn(TrackResolution.resolved(track));
    when(courseRepository.findAllOfSemesterFollowedByTrack(S5.getId(), track.getId()))
        .thenReturn(track == EL ? List.of(COMMON_S5, EL_ONLY) : List.of(COMMON_S5, TN_ONLY));
  }

  private List<String> refsOf(List<Course> courses) {
    return courses.stream().map(Course::getRef).toList();
  }

  @Test
  void an_el_student_follows_the_common_courses_and_the_el_ones() {
    studentExists();
    followsInS5(EL);

    var refs = refsOf(subject.findApplicable(STUDENT_ID, SemesterRef.S5));

    assertTrue(refs.contains("MATH5"), "a common course belongs to every programme");
    assertTrue(refs.contains("PROG_AV"));
  }

  @Test
  void an_el_student_never_sees_a_tn_course() {
    studentExists();
    followsInS5(EL);

    assertFalse(refsOf(subject.findApplicable(STUDENT_ID, SemesterRef.S5)).contains("TRANSFO"));
  }

  @Test
  void a_tn_student_never_sees_an_el_course() {
    studentExists();
    followsInS5(TN);

    var refs = refsOf(subject.findApplicable(STUDENT_ID, SemesterRef.S5));

    assertTrue(refs.contains("MATH5"));
    assertTrue(refs.contains("TRANSFO"));
    assertFalse(refs.contains("PROG_AV"));
  }

  @Test
  void every_student_follows_the_whole_common_core() {
    studentExists();
    when(trackChoiceService.resolve(STUDENT_ID, S2)).thenReturn(TrackResolution.commonCore());
    when(courseRepository.findAllBySemesterIdOrderByRefAsc(S2.getId()))
        .thenReturn(List.of(course("MATH2", 15, null), course("ALGO2", 15, null)));

    assertEquals(
        List.of("MATH2", "ALGO2"), refsOf(subject.findApplicable(STUDENT_ID, SemesterRef.S2)));
  }

  @Test
  void a_semester_without_a_track_choice_yields_no_course_at_all() {
    studentExists();
    when(trackChoiceService.resolve(STUDENT_ID, S5)).thenReturn(TrackResolution.notSelected());

    assertTrue(subject.findApplicable(STUDENT_ID, SemesterRef.S5).isEmpty());
  }

  @Test
  void a_track_semester_never_falls_back_to_listing_everything() {
    studentExists();
    when(trackChoiceService.resolve(STUDENT_ID, S5)).thenReturn(TrackResolution.notSelected());

    subject.findApplicable(STUDENT_ID, SemesterRef.S5);

    verify(courseRepository, org.mockito.Mockito.never())
        .findAllBySemesterIdOrderByRefAsc(S5.getId());
  }

  @Test
  void each_programme_totals_thirty_credits_over_the_same_semester() {
    studentExists();

    followsInS5(EL);
    var creditsForEl =
        subject.findApplicable(STUDENT_ID, SemesterRef.S5).stream()
            .mapToInt(Course::getCredits)
            .sum();

    followsInS5(TN);
    var creditsForTn =
        subject.findApplicable(STUDENT_ID, SemesterRef.S5).stream()
            .mapToInt(Course::getCredits)
            .sum();

    assertEquals(30, creditsForEl);
    assertEquals(30, creditsForTn);
    assertEquals(48, COMMON_S5.getCredits() + EL_ONLY.getCredits() + TN_ONLY.getCredits());
  }

  @Test
  void without_a_semester_every_one_of_them_is_resolved_on_its_own() {
    when(studentRepository.existsById(STUDENT_ID)).thenReturn(true);
    when(semesterRepository.findAllByOrderBySemOrderAsc()).thenReturn(List.of(S2, S5));
    when(trackChoiceService.resolve(STUDENT_ID, S2)).thenReturn(TrackResolution.commonCore());
    when(courseRepository.findAllBySemesterIdOrderByRefAsc(S2.getId()))
        .thenReturn(List.of(course("MATH2", 30, null)));
    followsInS5(EL);

    var refs = refsOf(subject.findApplicable(STUDENT_ID, null));

    assertEquals(List.of("MATH2", "MATH5", "PROG_AV"), refs);
  }

  @Test
  void a_student_in_the_common_core_still_gets_their_first_semesters() {
    when(studentRepository.existsById(STUDENT_ID)).thenReturn(true);
    when(semesterRepository.findAllByOrderBySemOrderAsc()).thenReturn(List.of(S2, S5));
    when(trackChoiceService.resolve(STUDENT_ID, S2)).thenReturn(TrackResolution.commonCore());
    when(courseRepository.findAllBySemesterIdOrderByRefAsc(S2.getId()))
        .thenReturn(List.of(course("MATH2", 30, null)));
    when(trackChoiceService.resolve(STUDENT_ID, S5)).thenReturn(TrackResolution.notSelected());

    assertEquals(List.of("MATH2"), refsOf(subject.findApplicable(STUDENT_ID, null)));
  }

  @Test
  void reading_a_curriculum_is_authorized_first() {
    studentExists();
    when(trackChoiceService.resolve(STUDENT_ID, S2)).thenReturn(TrackResolution.commonCore());
    when(courseRepository.findAllBySemesterIdOrderByRefAsc(S2.getId())).thenReturn(List.of());

    subject.findApplicable(STUDENT_ID, SemesterRef.S2);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void the_curriculum_of_an_unknown_student_is_not_found() {
    when(studentRepository.existsById(STUDENT_ID)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> subject.findApplicable(STUDENT_ID, SemesterRef.S2));
  }

  @Test
  void an_unknown_semester_is_not_found() {
    when(studentRepository.existsById(STUDENT_ID)).thenReturn(true);
    when(semesterRepository.findByRef(SemesterRef.S6)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findApplicable(STUDENT_ID, SemesterRef.S6));
  }
}
