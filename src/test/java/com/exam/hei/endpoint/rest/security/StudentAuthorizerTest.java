package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  private final StudentAuthorizer subject = new StudentAuthorizer(authenticatedResourceProvider);

  private void callerIs(Role role) {
    when(authenticatedResourceProvider.getAuthenticatedUser())
        .thenReturn(AppUser.builder().id(UUID.randomUUID()).role(role).build());
  }

  private void callerOwnsStudent(UUID studentId) {
    when(authenticatedResourceProvider.getAuthenticatedStudentId())
        .thenReturn(Optional.of(studentId));
  }

  @Test
  void an_admin_reads_any_student() {
    callerIs(Role.ADMIN);

    assertDoesNotThrow(() -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void a_teacher_reads_any_student() {
    callerIs(Role.TEACHER);

    assertDoesNotThrow(() -> subject.checkCanRead(UUID.randomUUID()));
  }

  @Test
  void a_non_student_role_is_not_even_resolved_to_a_profile() {
    // No point looking up a student record for an account that cannot own one.
    callerIs(Role.ADMIN);

    subject.checkCanRead(UUID.randomUUID());

    verify(authenticatedResourceProvider, never()).getAuthenticatedStudentId();
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
}
