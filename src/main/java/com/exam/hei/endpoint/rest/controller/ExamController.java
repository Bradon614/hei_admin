package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.ExamMapper;
import com.exam.hei.endpoint.rest.model.Exam;
import com.exam.hei.service.ExamService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of exams, always reached through the course that holds them. */
@RestController
@AllArgsConstructor
public class ExamController {

  private final ExamService examService;
  private final ExamMapper examMapper;

  @GetMapping("/courses/{courseId}/exams")
  public List<Exam> getCourseExams(@PathVariable UUID courseId) {
    return examService.findAllByCourseId(courseId).stream().map(examMapper::toRest).toList();
  }

  @PutMapping("/courses/{courseId}/exams")
  public List<Exam> crupdateCourseExams(
      @PathVariable UUID courseId, @RequestBody List<Exam> exams) {
    var saved = examService.saveAll(courseId, exams.stream().map(examMapper::toDomain).toList());
    return saved.stream().map(examMapper::toRest).toList();
  }
}
