package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Teacher;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single entry point for reading the authenticated caller.
 *
 * <p>Exists so that neither controllers nor services touch {@link SecurityContextHolder} directly:
 * authorization concerns stay inside this package, and a service that needs to scope a query to its
 * caller asks here instead of reading the security context itself.
 *
 * <p>Resolving which profile the caller is lives here too, so that "who is calling" has a single
 * answer rather than one lookup per feature that needs it.
 */
@Component
@AllArgsConstructor
public class AuthenticatedResourceProvider {

  private final StudentRepository studentRepository;
  private final TeacherRepository teacherRepository;

  public AppUser getAuthenticatedUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Principal principal)) {
      throw new IllegalStateException("No authenticated user in the current security context");
    }
    return principal.getUser();
  }

  /** Empty for any account that is not a student, an admin for instance. */
  public Optional<UUID> getAuthenticatedStudentId() {
    return studentRepository.findByUserId(getAuthenticatedUser().getId()).map(Student::getId);
  }

  /** Empty for any account that is not a teacher. */
  public Optional<UUID> getAuthenticatedTeacherId() {
    return teacherRepository.findByUserId(getAuthenticatedUser().getId()).map(Teacher::getId);
  }
}
