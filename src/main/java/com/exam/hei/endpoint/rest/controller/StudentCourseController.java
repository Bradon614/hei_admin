package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.CourseMapper;
import com.exam.hei.endpoint.rest.model.Course;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.service.StudentCourseService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class StudentCourseController {
  private final StudentCourseService studentCourseService;
  private final CourseMapper courseMapper;

  @GetMapping("/students/{studentId}/courses")
  public List<Course> getStudentCourses(
      @PathVariable UUID studentId,
      @RequestParam(name = "semester_ref", required = false) SemesterRef semesterRef) {
    return studentCourseService.findApplicable(studentId, semesterRef).stream()
        .map(courseMapper::toRest)
        .toList();
  }
}
