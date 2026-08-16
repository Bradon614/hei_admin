package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.CourseMapper;
import com.exam.hei.endpoint.rest.model.Course;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.service.CourseService;
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
public class CourseController {
  private final CourseService courseService;
  private final CourseMapper courseMapper;

  @GetMapping("/courses")
  public List<Course> getCourses(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "50") int pageSize,
      @RequestParam(name = "semester_ref", required = false) SemesterRef semesterRef,
      @RequestParam(name = "track_code", required = false) String trackCode) {
    return courseService.findAll(page, pageSize, semesterRef, trackCode).stream()
        .map(courseMapper::toRest)
        .toList();
  }

  @GetMapping("/courses/{id}")
  public Course getCourseById(@PathVariable UUID id) {
    return courseMapper.toRest(courseService.findById(id));
  }

  @PutMapping("/courses")
  public List<Course> crupdateCourses(@RequestBody List<Course> courses) {
    var saved = courseService.saveAll(courses.stream().map(courseMapper::toDomain).toList());
    return saved.stream().map(courseMapper::toRest).toList();
  }
}
