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
  private final TeacherAuthorizer teacherAuthorizer;

  public void checkCanRead(UUID studentId) {
    var role = authenticatedResourceProvider.getAuthenticatedUser().getRole();
    if (role == Role.ADMIN) {
      return;
    }
    if (role == Role.TEACHER) {
      teacherAuthorizer.checkTeaches(studentId);
      return;
    }
    checkIsOwnRecord(studentId);
  }

  public void checkIsSelf(UUID studentId) {
    var role = authenticatedResourceProvider.getAuthenticatedUser().getRole();
    if (role == Role.ADMIN) {
      return;
    }
    if (role != Role.STUDENT) {
      throw new ForbiddenException("Only an admin, or the student themselves, may reach this");
    }
    checkIsOwnRecord(studentId);
  }

  private void checkIsOwnRecord(UUID studentId) {
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
