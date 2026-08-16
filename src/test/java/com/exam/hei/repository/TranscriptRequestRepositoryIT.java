package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptRequest;
import com.exam.hei.repository.model.TranscriptStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class TranscriptRequestRepositoryIT extends FacadeIT {
  @Autowired TranscriptRequestRepository transcriptRequestRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired AppUserRepository appUserRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private AppUser account(Role role) {
    return appUserRepository.save(
        AppUser.builder().email(rand(12) + "@hei.test").passwordHash("hash").role(role).build());
  }

  private Student student() {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(account(Role.STUDENT))
            .build());
  }

  private TranscriptRequest.TranscriptRequestBuilder request(Student student) {
    return TranscriptRequest.builder().student(student).requestedBy(account(Role.ADMIN));
  }

  @Test
  void a_request_starts_pending_and_carries_no_file_yet() {
    var saved = transcriptRequestRepository.save(request(student()).build());

    var found = transcriptRequestRepository.findById(saved.getId()).orElseThrow();
    assertEquals(TranscriptStatus.PENDING, found.getStatus());
    assertNull(found.getS3Key());
    assertNull(found.getFileUrl());
    assertNull(found.getGeneratedAt());
    assertNull(found.getSentAt());
  }

  @Test
  void a_full_curriculum_request_names_no_semester() {
    var saved = transcriptRequestRepository.save(request(student()).build());

    assertNull(transcriptRequestRepository.findById(saved.getId()).orElseThrow().getSemester());
  }

  @Test
  void a_single_semester_request_carries_its_semester() {
    var s5 = semesterRepository.findByRef(SemesterRef.S5).orElseThrow();

    var saved = transcriptRequestRepository.save(request(student()).semester(s5).build());

    assertEquals(
        SemesterRef.S5,
        transcriptRequestRepository.findById(saved.getId()).orElseThrow().getSemester().getRef());
  }

  @Test
  void every_status_of_the_contract_round_trips() {
    var student = student();

    for (var status : TranscriptStatus.values()) {
      var saved = transcriptRequestRepository.save(request(student).status(status).build());

      assertEquals(
          status, transcriptRequestRepository.findById(saved.getId()).orElseThrow().getStatus());
    }
  }

  @Test
  void a_status_outside_the_contract_is_refused_by_the_database() {
    var student = student();
    var author = account(Role.ADMIN);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "insert into transcript_request (student_id, status, requested_by)"
                    + " values (?, 'ARCHIVED', ?)",
                student.getId(),
                author.getId()));
  }

  @Test
  void requests_are_listed_most_recent_first() {
    var student = student();
    var older =
        transcriptRequestRepository.save(
            request(student).requestedAt(Instant.parse("2026-01-01T08:00:00Z")).build());
    var newer =
        transcriptRequestRepository.save(
            request(student).requestedAt(Instant.parse("2026-03-01T08:00:00Z")).build());

    var found =
        transcriptRequestRepository.findAllByStudentIdOrderByRequestedAtDesc(student.getId());

    assertEquals(2, found.size());
    assertEquals(newer.getId(), found.get(0).getId());
    assertEquals(older.getId(), found.get(1).getId());
  }

  @Test
  void one_student_never_sees_another_student_s_requests() {
    var jean = student();
    var alice = student();
    transcriptRequestRepository.save(request(alice).build());

    assertEquals(
        0,
        transcriptRequestRepository.findAllByStudentIdOrderByRequestedAtDesc(jean.getId()).size());
  }

  @Test
  void a_failed_request_keeps_why_it_failed() {
    var saved =
        transcriptRequestRepository.save(
            request(student())
                .status(TranscriptStatus.FAILED)
                .errorMessage("The transcript could not be stored")
                .build());

    assertEquals(
        "The transcript could not be stored",
        transcriptRequestRepository.findById(saved.getId()).orElseThrow().getErrorMessage());
  }
}
