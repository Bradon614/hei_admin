package com.exam.hei.endpoint.rest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentAuthorizerTest {

  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final StudentAuthorizer subject =
      new StudentAuthorizer(authenticatedResourceProvider, studentRepository);

  private static final UUID CALLER_USER_ID = UUID.randomUUID();

  private void callerIs(Role role) {
    when(authenticatedResourceProvider.getAuthenticatedUser())
        .thenReturn(AppUser.builder().id(CALLER_USER_ID).role(role).build());
  }

  private void callerOwnsStudent(UUID studentId) {
    when(studentRepository.findByUserId(CALLER_USER_ID))
        .thenReturn(Optional.of(Student.builder().id(studentId).build()));
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
  void a_non_student_role_is_not_even_looked_up() {
    // No point querying the student table for an account that cannot own a record.
    callerIs(Role.ADMIN);

    subject.checkCanRead(UUID.randomUUID());

    verify(studentRepository, never()).findByUserId(CALLER_USER_ID);
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
    when(studentRepository.findByUserId(CALLER_USER_ID)).thenReturn(Optional.empty());

    assertThrows(ForbiddenException.class, () -> subject.checkCanRead(UUID.randomUUID()));
  }
}
