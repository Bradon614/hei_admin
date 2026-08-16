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

  public Optional<UUID> getAuthenticatedStudentId() {
    return studentRepository.findByUserId(getAuthenticatedUser().getId()).map(Student::getId);
  }

  public Optional<UUID> getAuthenticatedTeacherId() {
    return teacherRepository.findByUserId(getAuthenticatedUser().getId()).map(Teacher::getId);
  }
}
