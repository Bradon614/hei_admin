package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.TeachingAssignmentMapper;
import com.exam.hei.endpoint.rest.model.TeachingAssignment;
import com.exam.hei.service.TeachingAssignmentService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Role restrictions live in SecurityConf. */
@RestController
@AllArgsConstructor
public class TeachingAssignmentController {

  private final TeachingAssignmentService teachingAssignmentService;
  private final TeachingAssignmentMapper teachingAssignmentMapper;

  @GetMapping("/teachers/{teacherId}/teaching-assignments")
  public List<TeachingAssignment> getTeacherTeachingAssignments(@PathVariable UUID teacherId) {
    return teachingAssignmentService.findAllByTeacherId(teacherId).stream()
        .map(teachingAssignmentMapper::toRest)
        .toList();
  }

  @PutMapping("/teaching-assignments")
  public List<TeachingAssignment> crupdateTeachingAssignments(
      @RequestBody List<TeachingAssignment> assignments) {
    var saved =
        teachingAssignmentService.saveAll(
            assignments.stream().map(teachingAssignmentMapper::toDomain).toList());
    return saved.stream().map(teachingAssignmentMapper::toRest).toList();
  }
}
