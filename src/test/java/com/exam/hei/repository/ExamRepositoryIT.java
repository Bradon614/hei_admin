package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.SemesterRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ExamRepositoryIT extends FacadeIT {

  @Autowired ExamRepository examRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Course course() {
    return courseRepository.save(
        Course.builder()
            .ref(rand(20))
            .title("Course under test")
            .credits(6)
            .semester(semesterRepository.findByRef(SemesterRef.S1).orElseThrow())
            .build());
  }

  private Exam exam(Course course, String title, String coefficient, String date) {
    return examRepository.saveAndFlush(
        Exam.builder()
            .course(course)
            .title(title)
            .dateExam(Instant.parse(date))
            .coefficient(new BigDecimal(coefficient))
            .build());
  }

  @Test
  void an_exam_is_persisted_with_its_course_and_weight() {
    var course = course();

    var saved = exam(course, "Final", "2.00", "2026-01-15T08:00:00Z");

    var found = examRepository.findById(saved.getId()).orElseThrow();
    assertEquals(course.getId(), found.getCourse().getId());
    assertEquals(0, new BigDecimal("2.00").compareTo(found.getCoefficient()));
    assertEquals(Instant.parse("2026-01-15T08:00:00Z"), found.getDateExam());
  }

  @Test
  void a_course_may_hold_several_exams_ordered_by_date() {
    var course = course();
    exam(course, "Final", "2.00", "2026-01-15T08:00:00Z");
    exam(course, "Midterm", "1.00", "2025-11-20T08:00:00Z");

    var exams = examRepository.findAllByCourseIdOrderByDateExamAsc(course.getId());

    assertEquals(List.of("Midterm", "Final"), exams.stream().map(Exam::getTitle).toList());
  }

  @Test
  void two_exams_of_a_course_cannot_share_a_title() {
    var course = course();
    exam(course, "Final", "2.00", "2026-01-15T08:00:00Z");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> exam(course, "Final", "1.00", "2026-02-15T08:00:00Z"));
  }

  @Test
  void two_courses_may_each_have_an_exam_of_the_same_title() {
    exam(course(), "Final", "2.00", "2026-01-15T08:00:00Z");

    var otherCourse = course();
    assertEquals("Final", exam(otherCourse, "Final", "2.00", "2026-01-15T08:00:00Z").getTitle());
  }

  @Test
  void an_exam_carries_a_positive_coefficient_only() {
    var course = course();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> exam(course, "Weightless", "0.00", "2026-01-15T08:00:00Z"));
  }

  @Test
  void a_course_without_an_exam_has_none_listed() {
    assertTrue(examRepository.findAllByCourseIdOrderByDateExamAsc(course().getId()).isEmpty());
  }

  @Test
  void an_exam_is_findable_by_its_course_and_title() {
    var course = course();
    var saved = exam(course, "Final", "2.00", "2026-01-15T08:00:00Z");

    assertEquals(
        saved.getId(),
        examRepository.findByCourseIdAndTitle(course.getId(), "Final").orElseThrow().getId());
  }
}
