package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.TranscriptRequest;
import org.springframework.stereotype.Component;

@Component
public class TranscriptRequestMapper {
  public TranscriptRequest toRest(com.exam.hei.repository.model.TranscriptRequest domain) {
    return TranscriptRequest.builder()
        .id(domain.getId())
        .studentId(domain.getStudent().getId())
        .semesterRef(domain.getSemester() == null ? null : domain.getSemester().getRef())
        .status(domain.getStatus())
        .fileUrl(domain.getFileUrl())
        .requestedAt(domain.getRequestedAt())
        .generatedAt(domain.getGeneratedAt())
        .sentAt(domain.getSentAt())
        .errorMessage(domain.getErrorMessage())
        .build();
  }
}
