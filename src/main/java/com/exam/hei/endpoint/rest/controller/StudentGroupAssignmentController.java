package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.StudentGroupAssignmentMapper;
import com.exam.hei.endpoint.rest.model.StudentGroupAssignment;
import com.exam.hei.endpoint.rest.model.StudentGroupChange;
import com.exam.hei.service.StudentGroupAssignmentService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of the group membership history.
 *
 * <p>No permission check here: the rule keeping a student away from another student's history lives
 * in the security package and is applied by the service.
 */
@RestController
@AllArgsConstructor
public class StudentGroupAssignmentController {

  private final StudentGroupAssignmentService assignmentService;
  private final StudentGroupAssignmentMapper assignmentMapper;

  /**
   * Every group the student went through, or only the one in force on {@code at} when that date is
   * given.
   */
  @GetMapping("/students/{studentId}/group-assignments")
  public List<StudentGroupAssignment> getStudentGroupAssignments(
      @PathVariable UUID studentId,
      @RequestParam(name = "at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate at) {
    var assignments =
        at == null
            ? assignmentService.findAllByStudentId(studentId)
            : assignmentService.findActiveAt(studentId, at).stream().toList();
    return assignments.stream().map(assignmentMapper::toRest).toList();
  }

  @PostMapping("/students/{studentId}/group-assignments")
  public StudentGroupAssignment changeStudentGroup(
      @PathVariable UUID studentId, @RequestBody StudentGroupChange change) {
    return assignmentMapper.toRest(
        assignmentService.changeGroup(
            studentId, change.getGroupId(), change.getStartDate(), change.getReason()));
  }
}
