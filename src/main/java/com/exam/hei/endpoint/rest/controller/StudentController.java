package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.StudentMapper;
import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.service.StudentGroupAssignmentService;
import com.exam.hei.service.StudentService;
import com.exam.hei.service.StudentTrackChoiceService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No permission check here: role restrictions live in {@code SecurityConf}, and the rule keeping a
 * student away from another student's record lives in the security package too.
 */
@RestController
@AllArgsConstructor
public class StudentController {

  private final StudentService studentService;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentGroupAssignmentService assignmentService;
  private final StudentMapper studentMapper;

  @GetMapping("/students")
  public List<Student> getStudents(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "50") int pageSize,
      @RequestParam(name = "promotion_id", required = false) UUID promotionId,
      @RequestParam(name = "group_id", required = false) UUID groupId,
      @RequestParam(name = "at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate at,
      @RequestParam(name = "track_code", required = false) String trackCode) {
    return studentService.findAll(page, pageSize, promotionId, groupId, at, trackCode).stream()
        .map(this::enriched)
        .toList();
  }

  @GetMapping("/students/{id}")
  public Student getStudentById(@PathVariable UUID id) {
    return enriched(studentService.findById(id));
  }

  @PutMapping("/students")
  public List<Student> crupdateStudents(@RequestBody List<Student> students) {
    var saved = studentService.saveAll(students.stream().map(studentMapper::toDomain).toList());
    return saved.stream().map(this::enriched).toList();
  }

  /**
   * Adds the two computed fields of the payload. They are resolved per student rather than joined
   * in, which costs a query each on a listing: acceptable at the scale of a promotion, and worth
   * revisiting if a page ever grows large.
   */
  private Student enriched(com.exam.hei.repository.model.Student student) {
    return studentMapper.toRest(
        student,
        trackChoiceService.exitTrackOf(student.getId()).orElse(null),
        assignmentService.currentGroupOf(student.getId()).orElse(null));
  }
}
