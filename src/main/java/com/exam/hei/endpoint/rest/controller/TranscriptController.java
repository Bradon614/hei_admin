package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.TranscriptRequestMapper;
import com.exam.hei.endpoint.rest.model.TranscriptRequest;
import com.exam.hei.endpoint.rest.model.TranscriptRequestCreation;
import com.exam.hei.service.TranscriptService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class TranscriptController {
  private final TranscriptService transcriptService;
  private final TranscriptRequestMapper transcriptRequestMapper;

  @GetMapping("/students/{studentId}/transcript-requests")
  public List<TranscriptRequest> getStudentTranscriptRequests(@PathVariable UUID studentId) {
    return transcriptService.findAllByStudentId(studentId).stream()
        .map(transcriptRequestMapper::toRest)
        .toList();
  }

  @PostMapping("/students/{studentId}/transcript-requests")
  public ResponseEntity<TranscriptRequest> requestTranscript(
      @PathVariable UUID studentId, @RequestBody(required = false) TranscriptRequestCreation body) {
    var semesterRef = body == null ? null : body.getSemesterRef();
    var created = transcriptService.request(studentId, semesterRef);
    return ResponseEntity.accepted().body(transcriptRequestMapper.toRest(created));
  }

  @GetMapping("/transcript-requests/{id}")
  public TranscriptRequest getTranscriptRequestById(@PathVariable UUID id) {
    return transcriptRequestMapper.toRest(transcriptService.findById(id));
  }
}
