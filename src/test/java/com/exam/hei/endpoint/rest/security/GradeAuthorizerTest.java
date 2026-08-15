package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradeAuthorizerTest {

  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final TeacherAuthorizer teacherAuthorizer = mock(TeacherAuthorizer.class);
  private final GradeAuthorizer subject =
      new GradeAuthorizer(authenticatedResourceProvider, studentAuthorizer, teacherAuthorizer);

  private void callerIs(Role role) {
    when(authenticatedResourceProvider.getAuthenticatedUser())
        .thenReturn(AppUser.builder().id(UUID.randomUUID()).role(role).build());
  }

  // --- checkCanRead -------------------------------------------------------------

  @Test
  void an_admin_reads_any_grade() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkCanRead(UUID.randomUUID(), UUID.randomUUID()));

    verify(teacherAuthorizer, never()).checkCanEditExamsOf(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void the_owning_student_reads_their_grade() {
    callerIs(Role.STUDENT);
    var studentId = UUID.randomUUID();

    subject.checkCanRead(studentId, UUID.randomUUID());

    verify(studentAuthorizer).checkCanRead(studentId);
  }

  @Test
  void another_student_is_refused() {
    callerIs(Role.STUDENT);
    doThrow(new ForbiddenException("A student may only read their own record"))
        .when(studentAuthorizer)
        .checkCanRead(org.mockito.ArgumentMatchers.any());

    assertThrows(
        ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID(), UUID.randomUUID()));
  }

  @Test
  void an_assigned_teacher_reads_the_grade_of_their_course() {
    callerIs(Role.TEACHER);
    var courseId = UUID.randomUUID();

    subject.checkCanRead(UUID.randomUUID(), courseId);

    verify(teacherAuthorizer).checkCanEditExamsOf(courseId);
    verify(studentAuthorizer, never()).checkCanRead(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void an_unassigned_teacher_is_refused_even_though_being_a_teacher_would_pass_studentAuthorizer() {
    // The whole reason GradeAuthorizer exists rather than delegating straight to StudentAuthorizer:
    // that one waves any teacher through, which is wrong here.
    callerIs(Role.TEACHER);
    doThrow(new ForbiddenException("This teacher is not assigned to this course"))
        .when(teacherAuthorizer)
        .checkCanEditExamsOf(org.mockito.ArgumentMatchers.any());

    assertThrows(
        ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID(), UUID.randomUUID()));
  }

  // --- checkCanReadExamGrades ----------------------------------------------------

  @Test
  void a_student_never_reads_an_exam_s_grade_list() {
    callerIs(Role.STUDENT);

    assertThrows(ForbiddenException.class, () -> subject.checkCanReadExamGrades(UUID.randomUUID()));
  }

  @Test
  void an_admin_reads_any_exam_s_grade_list() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkCanReadExamGrades(UUID.randomUUID()));
  }

  @Test
  void an_assigned_teacher_reads_the_grade_list_of_their_course() {
    callerIs(Role.TEACHER);
    var courseId = UUID.randomUUID();

    subject.checkCanReadExamGrades(courseId);

    verify(teacherAuthorizer).checkCanEditExamsOf(courseId);
  }
}
