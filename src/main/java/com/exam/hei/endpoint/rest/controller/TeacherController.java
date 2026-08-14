package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.TeacherMapper;
import com.exam.hei.endpoint.rest.model.Teacher;
import com.exam.hei.service.TeacherService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of teachers. Role restrictions live in SecurityConf. */
@RestController
@AllArgsConstructor
public class TeacherController {

  private final TeacherService teacherService;
  private final TeacherMapper teacherMapper;

  @GetMapping("/teachers")
  public List<Teacher> getTeachers(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
    return teacherService.findAll(page, pageSize).stream().map(teacherMapper::toRest).toList();
  }

  @GetMapping("/teachers/{id}")
  public Teacher getTeacherById(@PathVariable UUID id) {
    return teacherMapper.toRest(teacherService.findById(id));
  }

  @PutMapping("/teachers")
  public List<Teacher> crupdateTeachers(@RequestBody List<Teacher> teachers) {
    var saved = teacherService.saveAll(teachers.stream().map(teacherMapper::toDomain).toList());
    return saved.stream().map(teacherMapper::toRest).toList();
  }
}
