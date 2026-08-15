package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.ExamRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class ExamService {

  private final ExamRepository examRepository;
  private final CourseService courseService;
  private final TeacherAuthorizer teacherAuthorizer;

  public List<Exam> findAllByCourseId(UUID courseId) {
    courseService.findById(courseId);
    return examRepository.findAllByCourseIdOrderByDateExamAsc(courseId);
  }

  public Exam findById(UUID id) {
    return examRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Exam " + id + " not found"));
  }

  @Transactional
  public List<Exam> saveAll(UUID courseId, List<Exam> exams) {
    teacherAuthorizer.checkCanEditExamsOf(courseId);
    var course = courseService.findById(courseId);
    exams.forEach(exam -> attachTo(course, exam));
    return examRepository.saveAll(exams);
  }

  private void attachTo(Course course, Exam exam) {
    if (exam.getId() != null) {
      checkBelongsTo(course, findById(exam.getId()));
    }
    checkWeighsSomething(exam);
    if (exam.getDateExam() == null) {
      throw new BadRequestException("An exam must state when it takes place");
    }
    exam.setCourse(course);
  }

  /** An exam is edited through the course that holds it, never moved from one course to another. */
  private void checkBelongsTo(Course course, Exam existing) {
    if (!existing.getCourse().getId().equals(course.getId())) {
      throw new BadRequestException(
          "Exam " + existing.getId() + " belongs to another course than " + course.getRef());
    }
  }

  /**
   * A weightless exam would contribute nothing to the final grade of its course while still
   * counting in the denominator, quietly dragging the result down.
   */
  private void checkWeighsSomething(Exam exam) {
    if (exam.getCoefficient() == null || exam.getCoefficient().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BadRequestException("An exam carries a coefficient greater than zero");
    }
  }
}
