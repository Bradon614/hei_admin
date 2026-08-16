package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.ResultMapper;
import com.exam.hei.endpoint.rest.mapper.StudentMapper;
import com.exam.hei.endpoint.rest.model.StudentResult;
import com.exam.hei.service.ResultService;
import com.exam.hei.service.StudentGroupAssignmentService;
import com.exam.hei.service.StudentTrackChoiceService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class ResultController {
  private final ResultService resultService;
  private final ResultMapper resultMapper;
  private final StudentMapper studentMapper;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentGroupAssignmentService assignmentService;

  @GetMapping("/students/{id}/result")
  public StudentResult getStudentResult(@PathVariable UUID id) {
    return toRest(resultService.resultOf(id));
  }

  @GetMapping("/promotions/{promotionId}/results")
  public List<StudentResult> getPromotionResults(
      @PathVariable UUID promotionId,
      @RequestParam(name = "track_code", required = false) String trackCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
    return resultService.resultsOfPromotion(promotionId, trackCode, page, pageSize).stream()
        .map(this::toRest)
        .toList();
  }

  private StudentResult toRest(com.exam.hei.model.StudentResult domain) {
    var student = domain.student();
    var restStudent =
        studentMapper.toRest(
            student,
            trackChoiceService.exitTrackOf(student.getId()).orElse(null),
            assignmentService.currentGroupOf(student.getId()).orElse(null));
    return resultMapper.toRest(domain, restStudent);
  }
}
