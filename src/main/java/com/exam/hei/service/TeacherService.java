package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class TeacherService {
  private final TeacherRepository teacherRepository;
  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TeacherAuthorizer teacherAuthorizer;

  public List<Teacher> findAll(int page, int pageSize) {
    return teacherRepository
        .findAll(Pagination.toPageRequest(page, pageSize, Sort.by("ref")))
        .getContent();
  }

  public Teacher findById(UUID id) {
    teacherAuthorizer.checkCanRead(id);
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

  private void attachAccount(Teacher teacher) {
    if (teacher.getId() == null) {
      if (teacher.getPassword() == null) {
        throw new BadRequestException("A password is required to create a teacher");
      }
      teacher.setUser(
          appUserRepository.save(
              AppUser.builder()
                  .email(teacher.getEmail())
                  .passwordHash(passwordEncoder.encode(teacher.getPassword()))
                  .role(Role.TEACHER)
                  .build()));
      return;
    }

    var existing = findById(teacher.getId()).getUser();
    var changed = false;
    if (!existing.getEmail().equals(teacher.getEmail())) {
      existing.setEmail(teacher.getEmail());
      changed = true;
    }
    if (teacher.getPassword() != null) {
      existing.setPasswordHash(passwordEncoder.encode(teacher.getPassword()));
      changed = true;
    }
    if (changed) {
      appUserRepository.save(existing);
    }
    teacher.setUser(existing);
  }
}
