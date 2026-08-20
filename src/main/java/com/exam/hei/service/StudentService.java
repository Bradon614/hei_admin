package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class StudentService {
  private final StudentRepository studentRepository;
  private final AppUserRepository appUserRepository;
  private final PromotionService promotionService;
  private final StudentAuthorizer studentAuthorizer;
  private final PasswordEncoder passwordEncoder;

  public List<Student> findAll(
      int page, int pageSize, UUID promotionId, UUID groupId, LocalDate at, String trackCode) {
    var pageRequest = Pagination.toPageRequest(page, pageSize, Sort.by("ref"));

    if (groupId != null) {
      var date = at == null ? LocalDate.now() : at;
      return studentRepository.findAllInGroupAt(groupId, date, pageRequest).getContent();
    }
    if (trackCode != null) {
      return studentRepository.findAllFollowingTrack(trackCode, pageRequest).getContent();
    }
    if (promotionId != null) {
      return studentRepository.findAllByPromotionId(promotionId, pageRequest).getContent();
    }
    return studentRepository.findAll(pageRequest).getContent();
  }

  public Student findById(UUID id) {
    studentAuthorizer.checkCanRead(id);
    return getById(id);
  }

  private Student getById(UUID id) {
    return studentRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Student " + id + " not found"));
  }

  public Student findByUserId(UUID userId) {
    return studentRepository
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("No student for user " + userId));
  }

  @Transactional
  public List<Student> saveAll(List<Student> students) {
    students.forEach(this::attachPromotion);
    students.forEach(this::attachAccount);
    return studentRepository.saveAll(students);
  }

  private void attachPromotion(Student student) {
    if (student.getId() != null) {
      getById(student.getId());
    }
    var promotionId = student.getPromotion() == null ? null : student.getPromotion().getId();
    if (promotionId == null) {
      throw new NotFoundException("A student must name the promotion it belongs to");
    }
    student.setPromotion(promotionService.findById(promotionId));
  }

  private void attachAccount(Student student) {
    if (student.getId() == null) {
      if (student.getPassword() == null) {
        throw new BadRequestException("A password is required to create a student");
      }
      student.setUser(
          appUserRepository.save(
              AppUser.builder()
                  .email(student.getEmail())
                  .passwordHash(passwordEncoder.encode(student.getPassword()))
                  .role(Role.STUDENT)
                  .build()));
      return;
    }

    var existing = getById(student.getId()).getUser();
    var changed = false;
    if (!existing.getEmail().equals(student.getEmail())) {
      existing.setEmail(student.getEmail());
      changed = true;
    }
    if (student.getPassword() != null) {
      existing.setPasswordHash(passwordEncoder.encode(student.getPassword()));
      existing.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1));
      changed = true;
    }
    if (changed) {
      appUserRepository.save(existing);
    }
    student.setUser(existing);
  }
}
