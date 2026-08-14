package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentTrackChoice;
import com.exam.hei.repository.model.Track;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Covers the resolution that makes a track depend on the semester rather than on the student. */
class StudentTrackChoiceRepositoryIT extends FacadeIT {

  @Autowired StudentTrackChoiceRepository trackChoiceRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired TrackRepository trackRepository;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Student student() {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(rand(5))
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(account)
            .build());
  }

  private Semester semester(SemesterRef ref) {
    return semesterRepository.findByRef(ref).orElseThrow();
  }

  private Track el() {
    return trackRepository.findByCode("EL").orElseThrow();
  }

  private Track tn() {
    return trackRepository.findByCode("TN").orElseThrow();
  }

  private StudentTrackChoice choose(Student student, Track track, SemesterRef from) {
    return trackChoiceRepository.saveAndFlush(
        StudentTrackChoice.builder()
            .student(student)
            .track(track)
            .fromSemester(semester(from))
            .build());
  }

  private UUID rulingTrackAt(Student student, SemesterRef ref) {
    return trackChoiceRepository
        .findRulingChoice(student.getId(), semester(ref).getSemOrder())
        .map(choice -> choice.getTrack().getId())
        .orElse(null);
  }

  // --- resolution -----------------------------------------------------------

  @Test
  void a_single_choice_rules_every_semester_from_its_own_onwards() {
    var jean = student();
    choose(jean, el(), SemesterRef.S4);

    assertEquals(el().getId(), rulingTrackAt(jean, SemesterRef.S4));
    assertEquals(el().getId(), rulingTrackAt(jean, SemesterRef.S5));
    assertEquals(el().getId(), rulingTrackAt(jean, SemesterRef.S6));
  }

  @Test
  void no_choice_rules_the_semesters_before_it() {
    // S1 to S3 are common core: nothing rules them, and that is not an error at this level.
    var jean = student();
    choose(jean, el(), SemesterRef.S4);

    assertTrue(trackChoiceRepository.findRulingChoice(jean.getId(), 1).isEmpty());
    assertTrue(trackChoiceRepository.findRulingChoice(jean.getId(), 3).isEmpty());
  }

  @Test
  void a_student_who_never_chose_is_ruled_by_nothing() {
    var jean = student();

    assertTrue(trackChoiceRepository.findRulingChoice(jean.getId(), 4).isEmpty());
  }

  // --- reorientation --------------------------------------------------------

  @Test
  void a_reorientation_leaves_the_semesters_already_covered_untouched() {
    // EL from S4, then TN from S5: S4 stays EL.
    var jean = student();
    choose(jean, el(), SemesterRef.S4);
    choose(jean, tn(), SemesterRef.S5);

    assertEquals(el().getId(), rulingTrackAt(jean, SemesterRef.S4));
    assertEquals(tn().getId(), rulingTrackAt(jean, SemesterRef.S5));
    assertEquals(tn().getId(), rulingTrackAt(jean, SemesterRef.S6));
  }

  @Test
  void the_exit_track_is_the_most_recent_choice() {
    var jean = student();
    choose(jean, el(), SemesterRef.S4);
    choose(jean, tn(), SemesterRef.S5);

    assertEquals(
        tn().getId(),
        trackChoiceRepository
            .findFirstByStudentIdOrderByFromSemesterSemOrderDesc(jean.getId())
            .orElseThrow()
            .getTrack()
            .getId());
  }

  @Test
  void the_choices_of_a_student_are_listed_in_order() {
    var jean = student();
    choose(jean, tn(), SemesterRef.S5);
    choose(jean, el(), SemesterRef.S4);

    var choices =
        trackChoiceRepository.findAllByStudentIdOrderByFromSemesterSemOrderAsc(jean.getId());

    assertEquals(2, choices.size());
    assertEquals(SemesterRef.S4, choices.get(0).getFromSemester().getRef());
    assertEquals(SemesterRef.S5, choices.get(1).getFromSemester().getRef());
  }

  // --- constraints ----------------------------------------------------------

  @Test
  void a_student_cannot_choose_twice_from_the_same_semester() {
    var jean = student();
    choose(jean, el(), SemesterRef.S4);

    assertThrows(DataIntegrityViolationException.class, () -> choose(jean, tn(), SemesterRef.S4));
  }

  @Test
  void two_students_choose_independently() {
    var jean = student();
    var alice = student();
    choose(jean, el(), SemesterRef.S4);
    choose(alice, tn(), SemesterRef.S4);

    assertEquals(el().getId(), rulingTrackAt(jean, SemesterRef.S4));
    assertEquals(tn().getId(), rulingTrackAt(alice, SemesterRef.S4));
  }
}
