package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Teacher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TeacherRepositoryIT extends FacadeIT {

  @Autowired TeacherRepository teacherRepository;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser persistedAccount() {
    return appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash("hash")
            .role(Role.TEACHER)
            .apiKey(UUID.randomUUID().toString())
            .build());
  }

  private Teacher.TeacherBuilder validTeacher() {
    return Teacher.builder()
        .ref(rand(20))
        .firstName("Aina")
        .lastName("Randria")
        .email(rand(12) + "@hei.test")
        .user(persistedAccount());
  }

  @Test
  void a_teacher_is_persisted_with_its_account() {
    var saved = teacherRepository.save(validTeacher().build());

    var found = teacherRepository.findById(saved.getId()).orElseThrow();

    assertEquals(saved.getRef(), found.getRef());
    assertEquals("Aina", found.getFirstName());
    assertEquals(saved.getUser().getId(), found.getUser().getId());
  }

  @Test
  void a_teacher_is_findable_by_its_ref_and_by_its_email() {
    var saved = teacherRepository.save(validTeacher().build());

    assertEquals(saved.getId(), teacherRepository.findByRef(saved.getRef()).orElseThrow().getId());
    assertEquals(
        saved.getId(), teacherRepository.findByEmail(saved.getEmail()).orElseThrow().getId());
  }

  @Test
  void a_teacher_is_findable_from_its_account() {
    var saved = teacherRepository.save(validTeacher().build());

    assertEquals(
        saved.getId(),
        teacherRepository.findByUserId(saved.getUser().getId()).orElseThrow().getId());
  }

  @Test
  void an_account_without_a_teacher_resolves_to_nothing() {
    assertTrue(teacherRepository.findByUserId(persistedAccount().getId()).isEmpty());
  }

  @Test
  void two_teachers_cannot_share_the_same_ref() {
    var ref = rand(20);
    teacherRepository.saveAndFlush(validTeacher().ref(ref).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> teacherRepository.saveAndFlush(validTeacher().ref(ref).build()));
  }

  @Test
  void two_teachers_cannot_share_the_same_email() {
    var email = rand(12) + "@hei.test";
    teacherRepository.saveAndFlush(validTeacher().email(email).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> teacherRepository.saveAndFlush(validTeacher().email(email).build()));
  }

  @Test
  void one_account_cannot_back_two_teachers() {
    var account = persistedAccount();
    teacherRepository.saveAndFlush(validTeacher().user(account).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> teacherRepository.saveAndFlush(validTeacher().user(account).build()));
  }
}
