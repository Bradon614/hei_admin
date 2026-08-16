package com.exam.hei.endpoint.rest.security;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GradeAuthorizer {
  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final StudentAuthorizer studentAuthorizer;
  private final TeacherAuthorizer teacherAuthorizer;

  public void checkCanRead(UUID studentId, UUID courseId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() == Role.TEACHER) {
      teacherAuthorizer.checkCanEditExamsOf(courseId);
      return;
    }
    studentAuthorizer.checkCanRead(studentId);
  }

  public void checkCanReadExamGrades(UUID courseId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() == Role.STUDENT) {
      throw new ForbiddenException("A student does not read the grade list of an exam");
    }
    teacherAuthorizer.checkCanEditExamsOf(courseId);
  }
}
