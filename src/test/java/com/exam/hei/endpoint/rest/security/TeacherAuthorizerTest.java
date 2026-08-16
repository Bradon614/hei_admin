package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeacherAuthorizerTest {
  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);
  private final TeachingAssignmentRepository teachingAssignmentRepository =
      mock(TeachingAssignmentRepository.class);
  private final TeacherAuthorizer subject =
      new TeacherAuthorizer(authenticatedResourceProvider, teachingAssignmentRepository);

  private void callerIs(Role role) {
    when(authenticatedResourceProvider.getAuthenticatedUser())
        .thenReturn(AppUser.builder().id(UUID.randomUUID()).role(role).build());
  }

  private void callerOwnsTeacher(UUID teacherId) {
    when(authenticatedResourceProvider.getAuthenticatedTeacherId())
        .thenReturn(Optional.of(teacherId));
  }

  @Test
  void an_admin_reads_any_teacher() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void a_teacher_reads_their_own_record() {
    var own = UUID.randomUUID();
    callerIs(Role.TEACHER);
    callerOwnsTeacher(own);

    assertDoesNotThrow(() -> subject.checkCanRead(own));
  }

  @Test
  void a_teacher_cannot_read_another_teacher() {
    callerIs(Role.TEACHER);
    callerOwnsTeacher(UUID.randomUUID());

    assertThrows(ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void a_student_cannot_read_a_teacher_s_teaching_scope() {
    callerIs(Role.STUDENT);

    assertThrows(ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void an_admin_edits_the_exams_of_any_course() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkCanEditExamsOf(UUID.randomUUID()));
  }

  @Test
  void an_assigned_teacher_edits_the_exams_of_their_course() {
    var teacherId = UUID.randomUUID();
    var courseId = UUID.randomUUID();
    callerIs(Role.TEACHER);
    callerOwnsTeacher(teacherId);
    when(teachingAssignmentRepository.existsByCourseIdAndTeacherId(courseId, teacherId))
        .thenReturn(true);

    assertDoesNotThrow(() -> subject.checkCanEditExamsOf(courseId));
  }

  @Test
  void an_unassigned_teacher_cannot_edit_the_exams_of_a_course() {
    var teacherId = UUID.randomUUID();
    var courseId = UUID.randomUUID();
    callerIs(Role.TEACHER);
    callerOwnsTeacher(teacherId);
    when(teachingAssignmentRepository.existsByCourseIdAndTeacherId(courseId, teacherId))
        .thenReturn(false);

    assertThrows(ForbiddenException.class, () -> subject.checkCanEditExamsOf(courseId));
  }

  @Test
  void a_teacher_account_without_a_record_edits_nothing() {
    callerIs(Role.TEACHER);
    when(authenticatedResourceProvider.getAuthenticatedTeacherId()).thenReturn(Optional.empty());

    assertThrows(ForbiddenException.class, () -> subject.checkCanEditExamsOf(UUID.randomUUID()));
  }
}
