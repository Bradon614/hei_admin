package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.GradeHistory;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class GradeRepositoryIT extends FacadeIT {
  @Autowired GradeRepository gradeRepository;
  @Autowired GradeHistoryRepository gradeHistoryRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired ExamRepository examRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Course course(SemesterRef semesterRef) {
    return courseRepository.save(
        Course.builder()
            .ref(rand(20))
            .title("Course under test")
            .credits(6)
            .semester(semesterRepository.findByRef(semesterRef).orElseThrow())
            .build());
  }

  private Exam exam(Course course) {
    return examRepository.save(
        Exam.builder()
            .course(course)
            .title("Final")
            .dateExam(Instant.parse("2026-01-15T08:00:00Z"))
            .coefficient(new BigDecimal("2.00"))
            .build());
  }

  private Student student() {
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
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
            .user(account)
            .build());
  }

  private AppUser admin() {
    return appUserRepository.save(
        AppUser.builder()
            .email(rand(12) + "@hei.test")
            .passwordHash("hash")
            .role(Role.ADMIN)
            .build());
  }

  private Grade.GradeBuilder grade(Student student, Exam exam, String value) {
    return Grade.builder()
        .student(student)
        .exam(exam)
        .value(new BigDecimal(value))
        .updatedAt(Instant.now());
  }

  @Test
  void a_grade_is_persisted_with_its_student_and_its_exam() {
    var course = course(SemesterRef.S1);
    var student = student();
    var exam = exam(course);

    var saved = gradeRepository.save(grade(student, exam, "14.00").build());

    var found = gradeRepository.findById(saved.getId()).orElseThrow();
    assertEquals(student.getId(), found.getStudent().getId());
    assertEquals(exam.getId(), found.getExam().getId());
    assertEquals(0, new BigDecimal("14.00").compareTo(found.getValue()));
  }

  @Test
  void a_student_cannot_have_two_grades_on_the_same_exam() {
    var course = course(SemesterRef.S1);
    var student = student();
    var exam = exam(course);
    gradeRepository.saveAndFlush(grade(student, exam, "14.00").build());

    assertThrows(
        DataIntegrityViolationException.class,
        () -> gradeRepository.saveAndFlush(grade(student, exam, "10.00").build()));
  }

  @Test
  void a_grade_is_bounded_between_zero_and_twenty() {
    var course = course(SemesterRef.S1);
    var exam = exam(course);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> gradeRepository.saveAndFlush(grade(student(), exam, "20.01").build()));
  }

  @Test
  void a_grade_is_findable_by_its_exam_and_its_student() {
    var course = course(SemesterRef.S1);
    var student = student();
    var exam = exam(course);
    var saved = gradeRepository.save(grade(student, exam, "14.00").build());

    assertEquals(
        saved.getId(),
        gradeRepository
            .findByExamIdAndStudentId(exam.getId(), student.getId())
            .orElseThrow()
            .getId());
  }

  @Test
  void grades_are_found_by_semester_through_their_exam_and_course() {
    var s1 = course(SemesterRef.S1);
    var s2 = course(SemesterRef.S2);
    var student = student();
    var inS1 = gradeRepository.save(grade(student, exam(s1), "14.00").build());
    gradeRepository.save(grade(student, exam(s2), "10.00").build());

    var found = gradeRepository.findAllByStudentIdAndSemesterRef(student.getId(), SemesterRef.S1);

    assertEquals(1, found.size());
    assertEquals(inS1.getId(), found.get(0).getId());
  }

  private GradeHistory.GradeHistoryBuilder history(
      Grade grade, BigDecimal oldValue, String newValue, AppUser author) {
    return GradeHistory.builder()
        .grade(grade)
        .oldValue(oldValue)
        .newValue(new BigDecimal(newValue))
        .reasonType(GradeChangeReasonType.CREATION)
        .reason("Initial entry")
        .changedBy(author);
  }

  @Test
  void a_history_row_records_the_transition_and_its_author() {
    var course = course(SemesterRef.S1);
    var grade = gradeRepository.save(grade(student(), exam(course), "14.00").build());
    var author = admin();

    var saved = gradeHistoryRepository.save(history(grade, null, "14.00", author).build());

    var found = gradeHistoryRepository.findById(saved.getId()).orElseThrow();
    assertEquals(null, found.getOldValue());
    assertEquals(0, new BigDecimal("14.00").compareTo(found.getNewValue()));
    assertEquals(author.getId(), found.getChangedBy().getId());
  }

  @Test
  void a_history_row_without_an_actual_change_is_rejected() {
    var course = course(SemesterRef.S1);
    var grade = gradeRepository.save(grade(student(), exam(course), "14.00").build());
    var author = admin();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            gradeHistoryRepository.saveAndFlush(
                GradeHistory.builder()
                    .grade(grade)
                    .oldValue(new BigDecimal("14.00"))
                    .newValue(new BigDecimal("14.00"))
                    .reasonType(GradeChangeReasonType.CORRECTION)
                    .reason("No-op")
                    .changedBy(author)
                    .build()));
  }

  @Test
  void a_history_row_without_a_reason_is_rejected() {
    var course = course(SemesterRef.S1);
    var grade = gradeRepository.save(grade(student(), exam(course), "14.00").build());
    var author = admin();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            gradeHistoryRepository.saveAndFlush(
                GradeHistory.builder()
                    .grade(grade)
                    .oldValue(null)
                    .newValue(new BigDecimal("14.00"))
                    .reasonType(GradeChangeReasonType.CREATION)
                    .reason("   ")
                    .changedBy(author)
                    .build()));
  }

  @Test
  void the_history_of_a_grade_is_returned_most_recent_first() {
    var course = course(SemesterRef.S1);
    var grade = gradeRepository.save(grade(student(), exam(course), "13.00").build());
    var author = admin();
    var first = gradeHistoryRepository.save(history(grade, null, "10.00", author).build());
    var second =
        gradeHistoryRepository.save(
            GradeHistory.builder()
                .grade(grade)
                .oldValue(new BigDecimal("10.00"))
                .newValue(new BigDecimal("13.00"))
                .reasonType(GradeChangeReasonType.CLAIM)
                .reason("Claim accepted")
                .changedBy(author)
                .build());

    var found = gradeHistoryRepository.findAllByGradeIdOrderByChangedAtDesc(grade.getId());

    assertEquals(second.getId(), found.get(0).getId());
    assertEquals(first.getId(), found.get(1).getId());
  }
}
