package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

class StudentRepositoryIT extends FacadeIT {

  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Promotion persistedPromotion() {
    return promotionRepository.save(
        Promotion.builder()
            .ref(rand(5))
            .name("Promotion under test")
            .startYear(2025)
            .endYear(2028)
            .build());
  }

  private AppUser persistedAccount() {
    return appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash("hash")
            .role(Role.STUDENT)
            .apiKey(UUID.randomUUID().toString())
            .build());
  }

  private Student.StudentBuilder validStudent() {
    return Student.builder()
        .ref(rand(20))
        .firstName("Jean")
        .lastName("Rakoto")
        .email(rand(12) + "@hei.test")
        .entranceDate(LocalDate.of(2025, 9, 1))
        .promotion(persistedPromotion())
        .user(persistedAccount());
  }

  @Test
  void a_student_is_persisted_with_its_promotion_and_account() {
    var saved = studentRepository.save(validStudent().build());

    var found = studentRepository.findById(saved.getId()).orElseThrow();

    assertEquals(saved.getRef(), found.getRef());
    assertEquals("Jean", found.getFirstName());
    assertEquals(saved.getPromotion().getId(), found.getPromotion().getId());
    assertEquals(saved.getUser().getId(), found.getUser().getId());
  }

  @Test
  void a_student_is_active_unless_stated_otherwise() {
    var saved = studentRepository.save(validStudent().build());

    assertEquals(
        StudentStatus.ACTIVE, studentRepository.findById(saved.getId()).orElseThrow().getStatus());
  }

  @Test
  void a_student_status_survives_a_round_trip() {
    var saved = studentRepository.save(validStudent().status(StudentStatus.SUSPENDED).build());

    assertEquals(
        StudentStatus.SUSPENDED,
        studentRepository.findById(saved.getId()).orElseThrow().getStatus());
  }

  @Test
  void a_student_is_findable_by_its_ref_and_by_its_email() {
    var saved = studentRepository.save(validStudent().build());

    assertEquals(saved.getId(), studentRepository.findByRef(saved.getRef()).orElseThrow().getId());
    assertEquals(
        saved.getId(), studentRepository.findByEmail(saved.getEmail()).orElseThrow().getId());
  }

  @Test
  void a_student_is_findable_from_its_account() {
    // This is what lets an authenticated caller be resolved to their own profile.
    var saved = studentRepository.save(validStudent().build());

    assertEquals(
        saved.getId(),
        studentRepository.findByUserId(saved.getUser().getId()).orElseThrow().getId());
  }

  @Test
  void an_account_without_a_student_resolves_to_nothing() {
    assertTrue(studentRepository.findByUserId(persistedAccount().getId()).isEmpty());
  }

  @Test
  void two_students_cannot_share_the_same_ref() {
    var ref = rand(20);
    studentRepository.saveAndFlush(validStudent().ref(ref).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> studentRepository.saveAndFlush(validStudent().ref(ref).build()));
  }

  @Test
  void two_students_cannot_share_the_same_email() {
    var email = rand(12) + "@hei.test";
    studentRepository.saveAndFlush(validStudent().email(email).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> studentRepository.saveAndFlush(validStudent().email(email).build()));
  }

  @Test
  void one_account_cannot_back_two_students() {
    var account = persistedAccount();
    studentRepository.saveAndFlush(validStudent().user(account).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> studentRepository.saveAndFlush(validStudent().user(account).build()));
  }

  @Test
  void a_student_cannot_be_created_without_an_entrance_date() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> studentRepository.saveAndFlush(validStudent().entranceDate(null).build()));
  }

  @Test
  void students_are_listed_by_promotion_only() {
    var promotion = persistedPromotion();
    studentRepository.save(validStudent().promotion(promotion).build());
    studentRepository.save(validStudent().promotion(promotion).build());
    studentRepository.save(validStudent().build());

    var page = studentRepository.findAllByPromotionId(promotion.getId(), PageRequest.of(0, 50));

    assertEquals(2, page.getTotalElements());
    assertTrue(
        page.getContent().stream()
            .allMatch(student -> student.getPromotion().getId().equals(promotion.getId())));
  }

  @Test
  void listing_students_of_a_promotion_is_paginated() {
    var promotion = persistedPromotion();
    studentRepository.save(validStudent().promotion(promotion).build());
    studentRepository.save(validStudent().promotion(promotion).build());

    var page = studentRepository.findAllByPromotionId(promotion.getId(), PageRequest.of(0, 1));

    assertEquals(1, page.getContent().size());
    assertEquals(2, page.getTotalElements());
  }
}
