package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.GradeHistoryMapper;
import com.exam.hei.endpoint.rest.mapper.GradeMapper;
import com.exam.hei.endpoint.rest.model.Grade;
import com.exam.hei.endpoint.rest.model.GradeChange;
import com.exam.hei.endpoint.rest.model.GradeHistory;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.service.GradeService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class GradeController {
  private final GradeService gradeService;
  private final GradeMapper gradeMapper;
  private final GradeHistoryMapper gradeHistoryMapper;

  @GetMapping("/students/{studentId}/grades")
  public List<Grade> getStudentGrades(
      @PathVariable UUID studentId,
      @RequestParam(name = "semester_ref", required = false) SemesterRef semesterRef) {
    return gradeService.findAllByStudentId(studentId, semesterRef).stream()
        .map(gradeMapper::toRest)
        .toList();
  }

  @GetMapping("/exams/{examId}/grades")
  public List<Grade> getExamGrades(@PathVariable UUID examId) {
    return gradeService.findAllByExamId(examId).stream().map(gradeMapper::toRest).toList();
  }

  @PutMapping("/exams/{examId}/grades")
  public List<Grade> crupdateExamGrades(
      @PathVariable UUID examId, @RequestBody List<GradeChange> changes) {
    var saved = gradeService.crupdate(examId, changes.stream().map(gradeMapper::toDomain).toList());
    return saved.stream().map(gradeMapper::toRest).toList();
  }

  @GetMapping("/grades/{id}")
  public Grade getGradeById(@PathVariable UUID id) {
    return gradeMapper.toRest(gradeService.findById(id));
  }

  @GetMapping("/grades/{id}/history")
  public List<GradeHistory> getGradeHistory(@PathVariable UUID id) {
    return gradeService.findHistoryOf(id).stream().map(gradeHistoryMapper::toRest).toList();
  }
}
