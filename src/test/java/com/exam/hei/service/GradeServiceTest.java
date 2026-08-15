package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.endpoint.rest.security.GradeAuthorizer;
import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.GradeChange;
import com.exam.hei.model.TrackResolution;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.GradeHistoryRepository;
import com.exam.hei.repository.GradeRepository;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentGroupAssignment;
import com.exam.hei.repository.model.TeachingAssignment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradeServiceTest {

  private final GradeRepository gradeRepository = mock(GradeRepository.class);
  private final GradeHistoryRepository gradeHistoryRepository = mock(GradeHistoryRepository.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final StudentGroupAssignmentRepository studentGroupAssignmentRepository =
      mock(StudentGroupAssignmentRepository.class);
  private final TeachingAssignmentRepository teachingAssignmentRepository =
      mock(TeachingAssignmentRepository.class);
  private final ExamService examService = mock(ExamService.class);
  private final StudentTrackChoiceService trackChoiceService =
      mock(StudentTrackChoiceService.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final TeacherAuthorizer teacherAuthorizer = mock(TeacherAuthorizer.class);
  private final GradeAuthorizer gradeAuthorizer = mock(GradeAuthorizer.class);
  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);

  private final GradeService subject =
      new GradeService(
          gradeRepository,
          gradeHistoryRepository,
          studentRepository,
          studentGroupAssignmentRepository,
          teachingAssignmentRepository,
          examService,
          trackChoiceService,
          studentAuthorizer,
          teacherAuthorizer,
          gradeAuthorizer,
          authenticatedResourceProvider);

  private static final UUID STUDENT_ID = UUID.randomUUID();
  private static final UUID EXAM_ID = UUID.randomUUID();
  private static final Student STUDENT = Student.builder().id(STUDENT_ID).ref("STD1").build();
  private static final Semester SEMESTER =
      Semester.builder().id(UUID.randomUUID()).ref(SemesterRef.S5).commonCore(false).build();
  private static final Course COURSE =
      Course.builder().id(UUID.randomUUID()).ref("PROG1").semester(SEMESTER).build();
  private static final Exam EXAM = Exam.builder().id(EXAM_ID).course(COURSE).build();
  private static final AppUser CALLER =
      AppUser.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();

  private void examAndStudentExist() {
    when(examService.findById(EXAM_ID)).thenReturn(EXAM);
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(trackChoiceService.resolve(STUDENT_ID, SEMESTER)).thenReturn(TrackResolution.commonCore());
    when(authenticatedResourceProvider.getAuthenticatedUser()).thenReturn(CALLER);
    when(gradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static GradeChange change(
      BigDecimal value, GradeChangeReasonType reasonType, String reason) {
    return new GradeChange(STUDENT_ID, value, reasonType, reason);
  }

  // --- creation and update ----------------------------------------------------

  @Test
  void a_first_grade_requires_reason_type_creation() {
    examAndStudentExist();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.empty());

    var saved =
        subject
            .crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("14.00"), GradeChangeReasonType.CREATION, "Initial entry")))
            .get(0);

    assertEquals(0, new BigDecimal("14.00").compareTo(saved.getValue()));
    assertEquals(STUDENT, saved.getStudent());
    assertEquals(EXAM, saved.getExam());
  }

  @Test
  void a_first_grade_with_another_reason_type_is_refused() {
    examAndStudentExist();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.empty());

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(change(new BigDecimal("14.00"), GradeChangeReasonType.CLAIM, "Because"))));
  }

  @Test
  void a_first_grade_writes_a_history_row_with_a_null_old_value() {
    examAndStudentExist();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.empty());

    subject.crupdate(
        EXAM_ID,
        List.of(change(new BigDecimal("14.00"), GradeChangeReasonType.CREATION, "Initial entry")));

    var captor =
        org.mockito.ArgumentCaptor.forClass(com.exam.hei.repository.model.GradeHistory.class);
    verify(gradeHistoryRepository).save(captor.capture());
    assertNull(captor.getValue().getOldValue());
    assertEquals(CALLER, captor.getValue().getChangedBy());
  }

  @Test
  void updating_an_existing_grade_cannot_use_reason_type_creation() {
    examAndStudentExist();
    var existing =
        Grade.builder()
            .id(UUID.randomUUID())
            .student(STUDENT)
            .exam(EXAM)
            .value(new BigDecimal("10.00"))
            .build();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.of(existing));

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("14.00"),
                        GradeChangeReasonType.CREATION,
                        "Claim accepted"))));
  }

  @Test
  void updating_an_existing_grade_records_the_previous_value() {
    examAndStudentExist();
    var existing =
        Grade.builder()
            .id(UUID.randomUUID())
            .student(STUDENT)
            .exam(EXAM)
            .value(new BigDecimal("10.00"))
            .build();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.of(existing));

    subject.crupdate(
        EXAM_ID,
        List.of(change(new BigDecimal("14.00"), GradeChangeReasonType.CLAIM, "Claim accepted")));

    var captor =
        org.mockito.ArgumentCaptor.forClass(com.exam.hei.repository.model.GradeHistory.class);
    verify(gradeHistoryRepository).save(captor.capture());
    assertEquals(0, new BigDecimal("10.00").compareTo(captor.getValue().getOldValue()));
    assertEquals(0, new BigDecimal("14.00").compareTo(captor.getValue().getNewValue()));
  }

  @Test
  void submitting_the_same_value_again_is_refused() {
    examAndStudentExist();
    var existing =
        Grade.builder()
            .id(UUID.randomUUID())
            .student(STUDENT)
            .exam(EXAM)
            .value(new BigDecimal("14.00"))
            .build();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.of(existing));

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("14.00"), GradeChangeReasonType.CORRECTION, "No change"))));
  }

  // --- value and reason validation ---------------------------------------------

  @Test
  void a_negative_grade_is_refused() {
    examAndStudentExist();

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("-1.00"),
                        GradeChangeReasonType.CREATION,
                        "Initial entry"))));
  }

  @Test
  void a_grade_above_twenty_is_refused() {
    examAndStudentExist();

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("20.01"),
                        GradeChangeReasonType.CREATION,
                        "Initial entry"))));
  }

  @Test
  void a_missing_reason_is_refused() {
    examAndStudentExist();

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(change(new BigDecimal("14.00"), GradeChangeReasonType.CREATION, " "))));
  }

  @Test
  void a_missing_reason_type_is_refused() {
    examAndStudentExist();

    assertThrows(
        BadRequestException.class,
        () ->
            subject.crupdate(
                EXAM_ID, List.of(change(new BigDecimal("14.00"), null, "Initial entry"))));
  }

  // --- authorization and track resolution ---------------------------------------

  @Test
  void writing_grades_is_authorized_against_the_exam_s_course() {
    examAndStudentExist();
    when(gradeRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
        .thenReturn(Optional.empty());

    subject.crupdate(
        EXAM_ID,
        List.of(change(new BigDecimal("14.00"), GradeChangeReasonType.CREATION, "Initial entry")));

    verify(teacherAuthorizer).checkCanEditExamsOf(COURSE.getId());
  }

  @Test
  void a_student_with_no_track_choice_covering_the_semester_is_rejected() {
    examAndStudentExist();
    when(trackChoiceService.resolve(STUDENT_ID, SEMESTER))
        .thenReturn(TrackResolution.notSelected());

    assertThrows(
        ConflictException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("14.00"),
                        GradeChangeReasonType.CREATION,
                        "Initial entry"))));
  }

  @Test
  void an_unknown_student_is_not_found() {
    when(examService.findById(EXAM_ID)).thenReturn(EXAM);
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () ->
            subject.crupdate(
                EXAM_ID,
                List.of(
                    change(
                        new BigDecimal("14.00"),
                        GradeChangeReasonType.CREATION,
                        "Initial entry"))));
  }

  // --- GET /exams/{id}/grades ----------------------------------------------------

  @Test
  void an_admin_sees_every_grade_of_an_exam() {
    when(examService.findById(EXAM_ID)).thenReturn(EXAM);
    when(authenticatedResourceProvider.getAuthenticatedUser()).thenReturn(CALLER);
    var grades = List.of(Grade.builder().id(UUID.randomUUID()).student(STUDENT).exam(EXAM).build());
    when(gradeRepository.findAllByExamId(EXAM_ID)).thenReturn(grades);

    assertEquals(grades, subject.findAllByExamId(EXAM_ID));

    verify(gradeAuthorizer).checkCanReadExamGrades(COURSE.getId());
  }

  @Test
  void a_teacher_only_sees_grades_of_students_in_a_group_they_cover_on_the_exam_date() {
    var dated =
        Exam.builder()
            .id(EXAM_ID)
            .course(COURSE)
            .dateExam(Instant.parse("2026-01-15T08:00:00Z"))
            .build();
    var teacherId = UUID.randomUUID();
    var teacherCaller = AppUser.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
    var coveredGroup = Group.builder().id(UUID.randomUUID()).build();
    var otherGroup = Group.builder().id(UUID.randomUUID()).build();
    var inCoveredGroup = STUDENT;
    var elsewhereStudent = Student.builder().id(UUID.randomUUID()).ref("STD2").build();

    when(examService.findById(EXAM_ID)).thenReturn(dated);
    when(authenticatedResourceProvider.getAuthenticatedUser()).thenReturn(teacherCaller);
    when(authenticatedResourceProvider.getAuthenticatedTeacherId())
        .thenReturn(Optional.of(teacherId));
    when(teachingAssignmentRepository.findAllByCourseIdAndTeacherId(COURSE.getId(), teacherId))
        .thenReturn(
            List.of(
                TeachingAssignment.builder()
                    .id(UUID.randomUUID())
                    .course(COURSE)
                    .group(coveredGroup)
                    .build()));

    var gradeInCoveredGroup =
        Grade.builder().id(UUID.randomUUID()).student(inCoveredGroup).exam(dated).build();
    var gradeElsewhere =
        Grade.builder().id(UUID.randomUUID()).student(elsewhereStudent).exam(dated).build();
    when(gradeRepository.findAllByExamId(EXAM_ID))
        .thenReturn(List.of(gradeInCoveredGroup, gradeElsewhere));

    when(studentGroupAssignmentRepository.findActiveAt(
            inCoveredGroup.getId(), LocalDate.of(2026, 1, 15)))
        .thenReturn(Optional.of(StudentGroupAssignment.builder().group(coveredGroup).build()));
    when(studentGroupAssignmentRepository.findActiveAt(
            elsewhereStudent.getId(), LocalDate.of(2026, 1, 15)))
        .thenReturn(Optional.of(StudentGroupAssignment.builder().group(otherGroup).build()));

    var found = subject.findAllByExamId(EXAM_ID);

    assertEquals(List.of(gradeInCoveredGroup), found);
  }

  // --- GET /students/{id}/grades --------------------------------------------------

  @Test
  void reading_a_student_s_grades_is_authorized_first() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(gradeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());

    subject.findAllByStudentId(STUDENT_ID, null);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void a_semester_filter_narrows_a_student_s_grades() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(gradeRepository.findAllByStudentIdAndSemesterRef(STUDENT_ID, SemesterRef.S5))
        .thenReturn(List.of());

    subject.findAllByStudentId(STUDENT_ID, SemesterRef.S5);

    verify(gradeRepository).findAllByStudentIdAndSemesterRef(STUDENT_ID, SemesterRef.S5);
    verify(gradeRepository, never()).findAllByStudentId(any());
  }

  // --- GET /grades/{id} and history -----------------------------------------------

  @Test
  void an_unknown_grade_is_not_found() {
    var id = UUID.randomUUID();
    when(gradeRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void reading_a_grade_is_authorized_against_its_student_and_its_course() {
    var id = UUID.randomUUID();
    var grade = Grade.builder().id(id).student(STUDENT).exam(EXAM).build();
    when(gradeRepository.findById(id)).thenReturn(Optional.of(grade));

    subject.findById(id);

    verify(gradeAuthorizer).checkCanRead(STUDENT_ID, COURSE.getId());
  }

  @Test
  void the_history_of_a_grade_reuses_the_same_authorization() {
    var id = UUID.randomUUID();
    var grade = Grade.builder().id(id).student(STUDENT).exam(EXAM).build();
    when(gradeRepository.findById(id)).thenReturn(Optional.of(grade));
    when(gradeHistoryRepository.findAllByGradeIdOrderByChangedAtDesc(id)).thenReturn(List.of());

    subject.findHistoryOf(id);

    verify(gradeAuthorizer).checkCanRead(STUDENT_ID, COURSE.getId());
  }
}
