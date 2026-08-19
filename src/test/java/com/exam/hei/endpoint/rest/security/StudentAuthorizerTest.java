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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentAuthorizerTest {
  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);
  private final TeacherAuthorizer teacherAuthorizer = mock(TeacherAuthorizer.class);
  private final StudentAuthorizer subject =
      new StudentAuthorizer(authenticatedResourceProvider, teacherAuthorizer);

  private void callerIs(Role role) {
    when(authenticatedResourceProvider.getAuthenticatedUser())
        .thenReturn(AppUser.builder().id(UUID.randomUUID()).role(role).build());
  }

  private void callerOwnsStudent(UUID studentId) {
    when(authenticatedResourceProvider.getAuthenticatedStudentId())
        .thenReturn(Optional.of(studentId));
  }

  private void teacherDoesNotTeach(UUID studentId) {
    doThrow(new ForbiddenException("This teacher does not teach this student"))
        .when(teacherAuthorizer)
        .checkTeaches(studentId);
  }

  @Test
  void an_admin_reads_any_student() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void an_admin_is_not_even_resolved_to_a_profile() {
    callerIs(Role.ADMIN);

    subject.checkCanRead(UUID.randomUUID());

    verify(authenticatedResourceProvider, never()).getAuthenticatedStudentId();
  }

  @Test
  void a_teacher_reads_a_student_they_teach() {
    callerIs(Role.TEACHER);

    assertDoesNotThrow(() -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void a_teacher_cannot_read_a_student_they_do_not_teach() {
    var other = UUID.randomUUID();
    callerIs(Role.TEACHER);
    teacherDoesNotTeach(other);

    assertThrows(ForbiddenException.class, () -> subject.checkCanRead(other));
  }

  @Test
  void a_student_reads_their_own_record() {
    var own = UUID.randomUUID();
    callerIs(Role.STUDENT);
    callerOwnsStudent(own);

    assertDoesNotThrow(() -> subject.checkCanRead(own));
  }

  @Test
  void a_student_cannot_read_another_student() {
    callerIs(Role.STUDENT);
    callerOwnsStudent(UUID.randomUUID());

    assertThrows(ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void a_student_account_without_a_record_reads_nothing() {
    callerIs(Role.STUDENT);
    when(authenticatedResourceProvider.getAuthenticatedStudentId()).thenReturn(Optional.empty());

    assertThrows(ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void an_admin_reaches_what_belongs_to_a_student_alone() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkIsSelf(UUID.randomUUID()));
  }

  @Test
  void a_teacher_never_reaches_what_belongs_to_a_student_alone() {
    callerIs(Role.TEACHER);

    assertThrows(ForbiddenException.class, () -> subject.checkIsSelf(UUID.randomUUID()));
  }

  @Test
  void a_teacher_is_refused_without_the_teaching_scope_even_being_consulted() {
    callerIs(Role.TEACHER);

    assertThrows(ForbiddenException.class, () -> subject.checkIsSelf(UUID.randomUUID()));

    verify(teacherAuthorizer, never()).checkTeaches(UUID.randomUUID());
  }

  @Test
  void a_student_reaches_their_own() {
    var own = UUID.randomUUID();
    callerIs(Role.STUDENT);
    callerOwnsStudent(own);

    assertDoesNotThrow(() -> subject.checkIsSelf(own));
  }

  @Test
  void a_student_never_reaches_another_student_s() {
    callerIs(Role.STUDENT);
    callerOwnsStudent(UUID.randomUUID());

    assertThrows(ForbiddenException.class, () -> subject.checkIsSelf(UUID.randomUUID()));
  }
}
