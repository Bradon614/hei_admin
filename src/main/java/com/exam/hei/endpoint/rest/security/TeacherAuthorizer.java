package com.exam.hei.endpoint.rest.security;

import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.Role;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class TeacherAuthorizer {
  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final TeachingAssignmentRepository teachingAssignmentRepository;

  public void checkCanRead(UUID teacherId) {
    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() == Role.ADMIN) {
      return;
    }

    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() != Role.TEACHER
        || !ownTeacherId().equals(teacherId)) {
      throw new ForbiddenException("Only an admin, or the teacher themselves, may read this");
    }
  }

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
