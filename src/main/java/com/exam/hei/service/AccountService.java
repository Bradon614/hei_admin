package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class AccountService {
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final String WRONG_PASSWORD = "Invalid password";

  private final AppUserRepository appUserRepository;
  private final StudentRepository studentRepository;
  private final TeacherRepository teacherRepository;
  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public AppUser setStudentAccountEnabled(UUID studentId, boolean enabled) {
    var student =
        studentRepository
            .findById(studentId)
            .orElseThrow(() -> new NotFoundException("Student " + studentId + " not found"));
    return setEnabled(student.getUser(), enabled);
  }

  @Transactional
  public AppUser setTeacherAccountEnabled(UUID teacherId, boolean enabled) {
    var teacher =
        teacherRepository
            .findById(teacherId)
            .orElseThrow(() -> new NotFoundException("Teacher " + teacherId + " not found"));
    return setEnabled(teacher.getUser(), enabled);
  }

  @Transactional
  public void changeOwnPassword(String currentPassword, String newPassword) {
    var user =
        appUserRepository
            .findById(authenticatedResourceProvider.getAuthenticatedUser().getId())
            .orElseThrow(() -> new BadCredentialsException(WRONG_PASSWORD));
    if (currentPassword == null
        || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new BadCredentialsException(WRONG_PASSWORD);
    }
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new BadRequestException(
          "A password is at least " + MIN_PASSWORD_LENGTH + " characters long");
    }
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw new BadRequestException("The new password is the current one");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1));
    appUserRepository.save(user);
  }

  private AppUser setEnabled(AppUser user, boolean enabled) {
    checkCallerIsAdmin();
    checkNotOwnAccount(user);
    user.setEnabled(enabled);
    return appUserRepository.save(user);
  }

  private void checkCallerIsAdmin() {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() != Role.ADMIN) {
      throw new ForbiddenException("Only an admin may change the state of an account");
    }
  }

  private void checkNotOwnAccount(AppUser user) {
    if (user.getId().equals(authenticatedResourceProvider.getAuthenticatedUser().getId())) {
      throw new ForbiddenException("An administrator cannot change the state of their own account");
    }
  }
}
