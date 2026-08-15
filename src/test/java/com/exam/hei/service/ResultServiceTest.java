package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.GraduationBlockerCode;
import com.exam.hei.model.SemesterResultStatus;
import com.exam.hei.model.TrackResolution;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.GradeRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Track;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class ResultServiceTest {

  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final SemesterRepository semesterRepository = mock(SemesterRepository.class);
  private final GradeRepository gradeRepository = mock(GradeRepository.class);
  private final ExamService examService = mock(ExamService.class);
  private final StudentCourseService studentCourseService = mock(StudentCourseService.class);
  private final StudentTrackChoiceService trackChoiceService =
      mock(StudentTrackChoiceService.class);
  private final PromotionService promotionService = mock(PromotionService.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);

  private final ResultService subject =
      new ResultService(
          studentRepository,
          semesterRepository,
          gradeRepository,
          examService,
          studentCourseService,
          trackChoiceService,
          promotionService,
          studentAuthorizer);

  private static final UUID STUDENT_ID = UUID.randomUUID();
  private static final Student STUDENT = Student.builder().id(STUDENT_ID).ref("STD1").build();
  private static final Track EL = Track.builder().id(UUID.randomUUID()).code("EL").build();

  private static Semester semester(SemesterRef ref, boolean commonCore) {
    return Semester.builder()
        .id(UUID.randomUUID())
        .ref(ref)
        .requiredCredits(30)
        .commonCore(commonCore)
        .build();
  }

  private static Course course(String ref, int credits) {
    return Course.builder().id(UUID.randomUUID()).ref(ref).credits(credits).build();
  }

  private static Exam exam(Course course, String coefficient) {
    return Exam.builder()
        .id(UUID.randomUUID())
        .course(course)
        .coefficient(new BigDecimal(coefficient))
        .build();
  }

  private static Grade gradeOf(Exam exam, String value) {
    return Grade.builder().exam(exam).value(new BigDecimal(value)).build();
  }

  private void studentExists() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(gradeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());
  }

  private void onlySemester(Semester semester) {
    when(semesterRepository.findAllByOrderBySemOrderAsc()).thenReturn(List.of(semester));
  }

  // --- course result ----------------------------------------------------------

  @Test
  void a_course_s_final_grade_weighs_each_exam_by_its_coefficient() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    var course = course("PROG1", 6);
    var exam1 = exam(course, "1.00");
    var exam2 = exam(course, "3.00");
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1))
        .thenReturn(List.of(course));
    when(examService.findAllByCourseId(course.getId())).thenReturn(List.of(exam1, exam2));
    when(gradeRepository.findAllByStudentId(STUDENT_ID))
        .thenReturn(List.of(gradeOf(exam1, "8.00"), gradeOf(exam2, "16.00")));

    var result = subject.resultOf(STUDENT_ID);

    // (8*1 + 16*3) / (1+3) = 56/4 = 14.00
    var courseResult = result.semesterResults().get(0).courseResults().get(0);
    assertEquals(0, new BigDecimal("14.00").compareTo(courseResult.finalGrade()));
    assertTrue(courseResult.validated());
    assertEquals(6, courseResult.obtainedCredits());
  }

  @Test
  void a_missing_grade_counts_as_zero_but_keeps_its_coefficient_in_the_denominator() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    var course = course("PROG1", 6);
    var sat = exam(course, "1.00");
    var missed = exam(course, "1.00");
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1))
        .thenReturn(List.of(course));
    when(examService.findAllByCourseId(course.getId())).thenReturn(List.of(sat, missed));
    when(gradeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of(gradeOf(sat, "20.00")));

    var courseResult = subject.resultOf(STUDENT_ID).semesterResults().get(0).courseResults().get(0);

    // (20*1 + 0*1) / (1+1) = 10.00
    assertEquals(0, new BigDecimal("10.00").compareTo(courseResult.finalGrade()));
  }

  @Test
  void a_course_with_no_exam_at_all_has_no_final_grade() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    var course = course("PROG1", 6);
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1))
        .thenReturn(List.of(course));
    when(examService.findAllByCourseId(course.getId())).thenReturn(List.of());

    var courseResult = subject.resultOf(STUDENT_ID).semesterResults().get(0).courseResults().get(0);

    assertNull(courseResult.finalGrade());
    assertFalse(courseResult.validated());
    assertEquals(0, courseResult.obtainedCredits());
  }

  @Test
  void a_course_below_ten_is_not_validated() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    var course = course("PROG1", 6);
    var exam = exam(course, "1.00");
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1))
        .thenReturn(List.of(course));
    when(examService.findAllByCourseId(course.getId())).thenReturn(List.of(exam));
    when(gradeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of(gradeOf(exam, "9.99")));

    var courseResult = subject.resultOf(STUDENT_ID).semesterResults().get(0).courseResults().get(0);

    assertFalse(courseResult.validated());
    assertEquals(0, courseResult.obtainedCredits());
  }

  // --- semester result ----------------------------------------------------------

  @Test
  void a_semester_without_a_track_choice_is_not_evaluable() {
    studentExists();
    var s5 = semester(SemesterRef.S5, false);
    onlySemester(s5);
    when(trackChoiceService.resolve(STUDENT_ID, s5)).thenReturn(TrackResolution.notSelected());

    var semesterResult = subject.resultOf(STUDENT_ID).semesterResults().get(0);

    assertEquals(SemesterResultStatus.TRACK_NOT_SELECTED, semesterResult.status());
    assertEquals(0, semesterResult.obtainedCredits());
    assertFalse(semesterResult.validated());
    assertTrue(semesterResult.courseResults().isEmpty());
    assertNull(semesterResult.track());

    verify(studentCourseService, org.mockito.Mockito.never())
        .findApplicable(STUDENT_ID, SemesterRef.S5);
  }

  @Test
  void a_common_core_semester_carries_no_track() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1)).thenReturn(List.of());

    assertNull(subject.resultOf(STUDENT_ID).semesterResults().get(0).track());
  }

  @Test
  void a_resolved_semester_carries_its_track() {
    studentExists();
    var s5 = semester(SemesterRef.S5, false);
    onlySemester(s5);
    when(trackChoiceService.resolve(STUDENT_ID, s5)).thenReturn(TrackResolution.resolved(EL));
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S5)).thenReturn(List.of());

    assertEquals(EL, subject.resultOf(STUDENT_ID).semesterResults().get(0).track());
  }

  @Test
  void a_semester_is_validated_once_required_credits_are_reached() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    var course = course("PROG1", 30);
    var exam = exam(course, "1.00");
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1))
        .thenReturn(List.of(course));
    when(examService.findAllByCourseId(course.getId())).thenReturn(List.of(exam));
    when(gradeRepository.findAllByStudentId(STUDENT_ID))
        .thenReturn(List.of(gradeOf(exam, "10.00")));

    assertTrue(subject.resultOf(STUDENT_ID).semesterResults().get(0).validated());
  }

  // --- student result -------------------------------------------------------------

  @Test
  void the_general_average_excludes_courses_with_no_grade_at_all() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    onlySemester(s1);
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    var graded = course("PROG1", 6);
    var ungraded = course("PROG2", 12);
    var exam = exam(graded, "1.00");
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1))
        .thenReturn(List.of(graded, ungraded));
    when(examService.findAllByCourseId(graded.getId())).thenReturn(List.of(exam));
    when(examService.findAllByCourseId(ungraded.getId())).thenReturn(List.of());
    when(gradeRepository.findAllByStudentId(STUDENT_ID))
        .thenReturn(List.of(gradeOf(exam, "16.00")));

    var average = subject.resultOf(STUDENT_ID).generalAverage();

    // Only the graded course counts: 16.00, not diluted by the ungradable one.
    assertEquals(0, new BigDecimal("16.00").compareTo(average));
  }

  @Test
  void a_student_graduates_only_when_every_semester_is_validated() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    var s2 = semester(SemesterRef.S2, true);
    when(semesterRepository.findAllByOrderBySemOrderAsc()).thenReturn(List.of(s1, s2));
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    when(trackChoiceService.resolve(STUDENT_ID, s2)).thenReturn(TrackResolution.notSelected());
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1)).thenReturn(List.of());

    var result = subject.resultOf(STUDENT_ID);

    assertFalse(result.graduated());
  }

  @Test
  void blockers_distinguish_a_failed_semester_from_a_non_evaluable_one() {
    studentExists();
    var s1 = semester(SemesterRef.S1, true);
    var s5 = semester(SemesterRef.S5, false);
    when(semesterRepository.findAllByOrderBySemOrderAsc()).thenReturn(List.of(s1, s5));
    when(trackChoiceService.resolve(STUDENT_ID, s1)).thenReturn(TrackResolution.commonCore());
    when(studentCourseService.findApplicable(STUDENT_ID, SemesterRef.S1)).thenReturn(List.of());
    when(trackChoiceService.resolve(STUDENT_ID, s5)).thenReturn(TrackResolution.notSelected());

    var blockers = subject.resultOf(STUDENT_ID).blockers();

    assertEquals(2, blockers.size());
    assertEquals(GraduationBlockerCode.SEMESTER_NOT_VALIDATED, blockers.get(0).code());
    assertEquals(SemesterRef.S1, blockers.get(0).semesterRef());
    assertEquals(GraduationBlockerCode.TRACK_NOT_SELECTED, blockers.get(1).code());
    assertEquals(SemesterRef.S5, blockers.get(1).semesterRef());
  }

  @Test
  void reading_a_result_is_authorized_first() {
    studentExists();
    onlySemester(semester(SemesterRef.S1, true));
    when(trackChoiceService.resolve(
            org.mockito.ArgumentMatchers.eq(STUDENT_ID), org.mockito.ArgumentMatchers.any()))
        .thenReturn(TrackResolution.commonCore());
    when(studentCourseService.findApplicable(
            org.mockito.ArgumentMatchers.eq(STUDENT_ID), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

    subject.resultOf(STUDENT_ID);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void an_unknown_student_s_result_is_not_found() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.resultOf(STUDENT_ID));
  }

  // --- promotion listing ----------------------------------------------------------

  @Test
  void an_unknown_promotion_is_not_found() {
    var id = UUID.randomUUID();
    when(promotionService.findById(id)).thenThrow(new NotFoundException("Promotion not found"));

    assertThrows(NotFoundException.class, () -> subject.resultsOfPromotion(id, null, 1, 50));
  }

  @Test
  void without_a_track_filter_every_student_of_the_promotion_is_listed() {
    var promotionId = UUID.randomUUID();
    when(promotionService.findById(promotionId))
        .thenReturn(Promotion.builder().id(promotionId).build());
    when(studentRepository.findAllByPromotionId(
            org.mockito.ArgumentMatchers.eq(promotionId), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.resultsOfPromotion(promotionId, null, 1, 50);

    verify(studentRepository)
        .findAllByPromotionId(
            org.mockito.ArgumentMatchers.eq(promotionId), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void a_track_filter_narrows_the_promotion_listing() {
    var promotionId = UUID.randomUUID();
    when(promotionService.findById(promotionId))
        .thenReturn(Promotion.builder().id(promotionId).build());
    when(studentRepository.findAllByPromotionIdFollowingTrack(
            org.mockito.ArgumentMatchers.eq(promotionId),
            org.mockito.ArgumentMatchers.eq("EL"),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(Page.empty(PageRequest.of(0, 50)));

    subject.resultsOfPromotion(promotionId, "EL", 1, 50);

    verify(studentRepository)
        .findAllByPromotionIdFollowingTrack(
            org.mockito.ArgumentMatchers.eq(promotionId),
            org.mockito.ArgumentMatchers.eq("EL"),
            org.mockito.ArgumentMatchers.any());
  }
}
