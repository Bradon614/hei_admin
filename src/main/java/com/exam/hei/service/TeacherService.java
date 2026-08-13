package com.exam.hei.service;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.TeacherRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Teacher;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class TeacherService {

  /** See the note on the student side: the column is required, the value is unusable on purpose. */
  private static final String NO_PASSWORD = "no-password-api-key-authentication-only";

  private final TeacherRepository teacherRepository;
  private final AppUserRepository appUserRepository;

  /**
   * @param page 1-based, as declared in doc/api.yml
   */
  public List<Teacher> findAll(int page, int pageSize) {
    return teacherRepository
        .findAll(Pagination.toPageRequest(page, pageSize, Sort.by("ref")))
        .getContent();
  }

  public Teacher findById(UUID id) {
    return teacherRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Teacher " + id + " not found"));
  }

  public Teacher findByUserId(UUID userId) {
    return teacherRepository
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("No teacher for user " + userId));
  }

  @Transactional
  public List<Teacher> saveAll(List<Teacher> teachers) {
    teachers.forEach(this::attachAccount);
    return teacherRepository.saveAll(teachers);
  }

  /** Same reasoning as for students: no account endpoint exists, so the profile creates its own. */
  private void attachAccount(Teacher teacher) {
    if (teacher.getId() == null) {
      teacher.setUser(
          appUserRepository.save(
              AppUser.builder()
                  .email(teacher.getEmail())
                  .passwordHash(NO_PASSWORD)
                  .role(Role.TEACHER)
                  .apiKey(UUID.randomUUID().toString())
                  .build()));
      return;
    }

    var existing = findById(teacher.getId()).getUser();
    if (!existing.getEmail().equals(teacher.getEmail())) {
      existing.setEmail(teacher.getEmail());
      appUserRepository.save(existing);
    }
    teacher.setUser(existing);
  }
}
