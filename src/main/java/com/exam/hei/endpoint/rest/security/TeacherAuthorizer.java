package com.exam.hei.endpoint.rest.security;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Two rules that compare the caller to the resource being reached, and therefore cannot live in the
 * filter chain next to the {@code hasRole} rules of {@code SecurityConf}.
 */
@Component
@AllArgsConstructor
public class TeacherAuthorizer {

  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final TeachingAssignmentRepository teachingAssignmentRepository;

  /**
   * Passes for an admin, and for the teacher whose own record is being read. Unlike {@link
   * StudentAuthorizer#checkCanRead}, a third role is not waved through: nothing gives one teacher a
   * business reason to browse another's teaching scope.
   */
  public void checkCanRead(UUID teacherId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() == Role.ADMIN) {
      return;
    }

    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() != Role.TEACHER
        || !ownTeacherId().equals(teacherId)) {
      throw new ForbiddenException("Only an admin, or the teacher themselves, may read this");
    }
  }

  /**
   * Passes for an admin, and for a teacher only when a teaching assignment names them for that
   * course, in any group: the exam belongs to the course, not to a group.
   */
  public void checkCanEditExamsOf(UUID courseId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() != Role.TEACHER) {
      return;
    }

    if (!teachingAssignmentRepository.existsByCourseIdAndTeacherId(courseId, ownTeacherId())) {
      throw new ForbiddenException("This teacher is not assigned to this course");
    }
  }

  private UUID ownTeacherId() {
    return authenticatedResourceProvider
        .getAuthenticatedTeacherId()
        .orElseThrow(
            () -> new ForbiddenException("This account is not linked to a teacher record"));
  }
}
