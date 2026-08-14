package com.exam.hei.endpoint.rest.security;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps a student away from another student's data.
 *
 * <p>This rule cannot be expressed as a role restriction: it compares the caller to the resource
 * being reached, so it cannot live in the filter chain next to the {@code hasRole} rules. It stays
 * in the security package all the same, and services call it rather than reimplementing it.
 *
 * <p>The same check will back the grade and transcript endpoints, where the specification states it
 * explicitly.
 */
@Component
@AllArgsConstructor
public class StudentAuthorizer {

  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final StudentRepository studentRepository;

  /** Passes for an admin or a teacher, and for the student whose own record is being read. */
  public void checkCanRead(UUID studentId) {
    var caller = authenticatedResourceProvider.getAuthenticatedUser();
    if (caller.getRole() != Role.STUDENT) {
      return;
    }

    var own =
        studentRepository
            .findByUserId(caller.getId())
            .orElseThrow(
                () -> new ForbiddenException("This account is not linked to a student record"));

    if (!own.getId().equals(studentId)) {
      throw new ForbiddenException("A student may only read their own record");
    }
  }
}
