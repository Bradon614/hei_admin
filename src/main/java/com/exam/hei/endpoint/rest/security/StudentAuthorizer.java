package com.exam.hei.endpoint.rest.security;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StudentAuthorizer {
  private final AuthenticatedResourceProvider authenticatedResourceProvider;

  public void checkCanRead(UUID studentId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() != Role.STUDENT) {
      return;
    }

    var own =
        authenticatedResourceProvider
            .getAuthenticatedStudentId()
            .orElseThrow(
                () -> new ForbiddenException("This account is not linked to a student record"));

    if (!own.equals(studentId)) {
      throw new ForbiddenException("A student may only read their own record");
    }
  }
}
