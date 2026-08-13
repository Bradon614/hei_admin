package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.StudentMapper;
import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.service.StudentService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of students.
 *
 * <p>No permission check here: role restrictions live in {@code SecurityConf}, and the rule keeping
 * a student away from another student's record lives in the security package too.
 *
 * <p>The {@code track_code}, {@code group_id} and {@code at} filters of doc/api.yml are not
 * accepted yet: they need the track choice and the group assignment history, which their own
 * features bring.
 */
@RestController
@AllArgsConstructor
public class StudentController {

  private final StudentService studentService;
  private final StudentMapper studentMapper;

  @GetMapping("/students")
  public List<Student> getStudents(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "50") int pageSize,
      @RequestParam(name = "promotion_id", required = false) UUID promotionId) {
    return studentService.findAll(page, pageSize, promotionId).stream()
        .map(studentMapper::toRest)
        .toList();
  }

  @GetMapping("/students/{id}")
  public Student getStudentById(@PathVariable UUID id) {
    return studentMapper.toRest(studentService.findById(id));
  }

  @PutMapping("/students")
  public List<Student> crupdateStudents(@RequestBody List<Student> students) {
    var saved = studentService.saveAll(students.stream().map(studentMapper::toDomain).toList());
    return saved.stream().map(studentMapper::toRest).toList();
  }
}
