package com.exam.hei.endpoint.rest.security;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may look at a grade, composed from {@link StudentAuthorizer} and {@link TeacherAuthorizer}
 * rather than reimplementing either: a grade is visible to the student it belongs to and to a
 * teacher assigned to its course, and both rules already exist elsewhere.
 */
@Component
@AllArgsConstructor
public class GradeAuthorizer {

  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final StudentAuthorizer studentAuthorizer;
  private final TeacherAuthorizer teacherAuthorizer;

  /**
   * Passes for an admin, the student the grade belongs to, or a teacher assigned to the exam's
   * course.
   *
   * <p>A teacher is checked first: {@link StudentAuthorizer#checkCanRead} would otherwise wave them
   * through unconditionally, which is correct for a student's own profile but not here, where being
   * a teacher at all is not enough — they must be assigned to that course.
   */
  public void checkCanRead(UUID studentId, UUID courseId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() == Role.TEACHER) {
      teacherAuthorizer.checkCanEditExamsOf(courseId);
      return;
    }
    studentAuthorizer.checkCanRead(studentId);
  }

  /**
   * Passes for an admin or a teacher assigned to the course. A student is never allowed here: the
   * specification gives them {@code GET /students/{id}/grades} for their own grades instead.
   */
  public void checkCanReadExamGrades(UUID courseId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() == Role.STUDENT) {
      throw new ForbiddenException("A student does not read the grade list of an exam");
    }
    teacherAuthorizer.checkCanEditExamsOf(courseId);
  }
}
