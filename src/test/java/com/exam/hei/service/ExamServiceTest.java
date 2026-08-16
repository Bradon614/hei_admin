package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExamServiceTest {
  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final CourseService courseService = mock(CourseService.class);
  private final TeacherAuthorizer teacherAuthorizer = mock(TeacherAuthorizer.class);
  private final ExamService subject =
      new ExamService(examRepository, courseService, teacherAuthorizer);

  private static final UUID COURSE_ID = UUID.randomUUID();
  private static final Course COURSE =
      Course.builder().id(COURSE_ID).ref("PROG1").title("Programming").credits(6).build();

  private void courseExists() {
    when(courseService.findById(COURSE_ID)).thenReturn(COURSE);
  }

  private void repositoryEchoesWhatItIsGiven() {
    when(examRepository.saveAll(any()))
        .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
  }

  private static Exam exam(String coefficient) {
    return Exam.builder()
        .title("Final")
        .dateExam(Instant.parse("2026-01-15T08:00:00Z"))
        .coefficient(coefficient == null ? null : new BigDecimal(coefficient))
        .build();
  }

  @Test
  void an_exam_is_attached_to_the_course_of_the_path() {
    courseExists();
    repositoryEchoesWhatItIsGiven();

    assertEquals(COURSE, subject.saveAll(COURSE_ID, List.of(exam("2.00"))).get(0).getCourse());
  }

  @Test
  void an_exam_of_an_unknown_course_is_not_found() {
    when(courseService.findById(COURSE_ID)).thenThrow(new NotFoundException("Course not found"));

    assertThrows(NotFoundException.class, () -> subject.saveAll(COURSE_ID, List.of(exam("2.00"))));
  }

  @Test
  void a_weightless_exam_is_refused() {
    courseExists();

    assertThrows(
        BadRequestException.class, () -> subject.saveAll(COURSE_ID, List.of(exam("0.00"))));
  }

  @Test
  void a_negative_coefficient_is_refused() {
    courseExists();

    assertThrows(
        BadRequestException.class, () -> subject.saveAll(COURSE_ID, List.of(exam("-1.00"))));
  }

  @Test
  void a_missing_coefficient_is_refused() {
    courseExists();

    assertThrows(BadRequestException.class, () -> subject.saveAll(COURSE_ID, List.of(exam(null))));
  }

  @Test
  void an_exam_states_when_it_takes_place() {
    courseExists();
    var undated = exam("2.00");
    undated.setDateExam(null);

    assertThrows(BadRequestException.class, () -> subject.saveAll(COURSE_ID, List.of(undated)));
  }

  @Test
  void an_exam_is_never_moved_to_another_course() {
    courseExists();
    var id = UUID.randomUUID();
    var elsewhere =
        Exam.builder()
            .id(id)
            .course(Course.builder().id(UUID.randomUUID()).ref("OTHER").build())
            .build();
    when(examRepository.findById(id)).thenReturn(Optional.of(elsewhere));

    var moved = exam("2.00");
    moved.setId(id);

    assertThrows(BadRequestException.class, () -> subject.saveAll(COURSE_ID, List.of(moved)));
  }

  @Test
  void an_exam_naming_an_unknown_id_is_not_found() {
    courseExists();
    var id = UUID.randomUUID();
    when(examRepository.findById(id)).thenReturn(Optional.empty());

    var unknown = exam("2.00");
    unknown.setId(id);

    assertThrows(NotFoundException.class, () -> subject.saveAll(COURSE_ID, List.of(unknown)));
  }

  @Test
  void writing_exams_is_authorized_before_the_course_is_even_resolved() {
    courseExists();
    repositoryEchoesWhatItIsGiven();

    subject.saveAll(COURSE_ID, List.of(exam("2.00")));

    verify(teacherAuthorizer).checkCanEditExamsOf(COURSE_ID);
  }

  @Test
  void an_unassigned_teacher_cannot_write_exams() {
    org.mockito.Mockito.doThrow(
            new ForbiddenException("This teacher is not assigned to this course"))
        .when(teacherAuthorizer)
        .checkCanEditExamsOf(COURSE_ID);

    assertThrows(ForbiddenException.class, () -> subject.saveAll(COURSE_ID, List.of(exam("2.00"))));
  }

  @Test
  void listing_the_exams_of_an_unknown_course_is_not_found() {
    when(courseService.findById(COURSE_ID)).thenThrow(new NotFoundException("Course not found"));

    assertThrows(NotFoundException.class, () -> subject.findAllByCourseId(COURSE_ID));
  }

  @Test
  void an_unknown_exam_is_not_found() {
    var id = UUID.randomUUID();
    when(examRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }
}
