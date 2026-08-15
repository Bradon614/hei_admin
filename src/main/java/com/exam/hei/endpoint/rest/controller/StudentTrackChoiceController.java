package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.StudentTrackChoiceMapper;
import com.exam.hei.endpoint.rest.model.StudentTrackChoice;
import com.exam.hei.endpoint.rest.model.StudentTrackChoiceCreation;
import com.exam.hei.service.StudentTrackChoiceService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Role restrictions live in SecurityConf. */
@RestController
@AllArgsConstructor
public class StudentTrackChoiceController {

  private final StudentTrackChoiceService trackChoiceService;
  private final StudentTrackChoiceMapper trackChoiceMapper;

  @GetMapping("/students/{studentId}/track-choices")
  public List<StudentTrackChoice> getStudentTrackChoices(@PathVariable UUID studentId) {
    return trackChoiceService.findAllByStudentId(studentId).stream()
        .map(trackChoiceMapper::toRest)
        .toList();
  }

  @PostMapping("/students/{studentId}/track-choices")
  public StudentTrackChoice chooseStudentTrack(
      @PathVariable UUID studentId, @RequestBody StudentTrackChoiceCreation creation) {
    return trackChoiceMapper.toRest(
        trackChoiceService.choose(
            studentId, creation.getTrackId(), creation.getFromSemesterRef(), creation.getReason()));
  }
}
