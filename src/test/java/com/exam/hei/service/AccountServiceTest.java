package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Teacher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceTest {
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final TeacherRepository teacherRepository = mock(TeacherRepository.class);
  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);
  private final AccountService subject =
      new AccountService(
          appUserRepository, studentRepository, teacherRepository, authenticatedResourceProvider);

  private static final UUID STUDENT_ID = UUID.randomUUID();
  private static final UUID TEACHER_ID = UUID.randomUUID();

  private static AppUser user(Role role) {
    return AppUser.builder().id(UUID.randomUUID()).role(role).enabled(true).build();
  }

  private void callerIs(AppUser caller) {
    when(authenticatedResourceProvider.getAuthenticatedUser()).thenReturn(caller);
  }

  private AppUser studentAccount() {
    var account = user(Role.STUDENT);
    when(studentRepository.findById(STUDENT_ID))
        .thenReturn(Optional.of(Student.builder().id(STUDENT_ID).user(account).build()));
    when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    return account;
  }

  private AppUser teacherAccount() {
    var account = user(Role.TEACHER);
    when(teacherRepository.findById(TEACHER_ID))
        .thenReturn(Optional.of(Teacher.builder().id(TEACHER_ID).user(account).build()));
    when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    return account;
  }

  @Test
  void an_admin_disables_a_student_account() {
    callerIs(user(Role.ADMIN));
    studentAccount();

    var saved = subject.setStudentAccountEnabled(STUDENT_ID, false);

    assertFalse(saved.isEnabled());
    verify(appUserRepository).save(saved);
  }

  @Test
  void an_admin_enables_a_student_account_again() {
    callerIs(user(Role.ADMIN));
    var account = studentAccount();
    account.setEnabled(false);

    assertTrue(subject.setStudentAccountEnabled(STUDENT_ID, true).isEnabled());
  }

  @Test
  void an_admin_disables_a_teacher_account() {
    callerIs(user(Role.ADMIN));
    teacherAccount();

    assertFalse(subject.setTeacherAccountEnabled(TEACHER_ID, false).isEnabled());
  }

  @Test
  void a_teacher_cannot_change_the_state_of_an_account() {
    callerIs(user(Role.TEACHER));
    studentAccount();

    assertThrows(
        ForbiddenException.class, () -> subject.setStudentAccountEnabled(STUDENT_ID, false));
    verify(appUserRepository, never()).save(any());
  }

  @Test
  void a_student_cannot_change_the_state_of_an_account() {
    callerIs(user(Role.STUDENT));
    studentAccount();

    assertThrows(
        ForbiddenException.class, () -> subject.setStudentAccountEnabled(STUDENT_ID, false));
    verify(appUserRepository, never()).save(any());
  }

  @Test
  void an_administrator_cannot_disable_the_account_they_are_signed_in_with() {
    var account = studentAccount();
    var caller = AppUser.builder().id(account.getId()).role(Role.ADMIN).enabled(true).build();
    callerIs(caller);

    assertThrows(
        ForbiddenException.class, () -> subject.setStudentAccountEnabled(STUDENT_ID, false));
    verify(appUserRepository, never()).save(any());
  }

  @Test
  void an_unknown_student_is_not_found() {
    callerIs(user(Role.ADMIN));
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> subject.setStudentAccountEnabled(STUDENT_ID, false));
  }

  @Test
  void an_unknown_teacher_is_not_found() {
    callerIs(user(Role.ADMIN));
    when(teacherRepository.findById(TEACHER_ID)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> subject.setTeacherAccountEnabled(TEACHER_ID, false));
  }
}
