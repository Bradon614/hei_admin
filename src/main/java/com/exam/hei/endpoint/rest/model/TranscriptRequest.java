package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.TranscriptStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TranscriptRequest {
  private UUID id;
  private UUID studentId;

  private SemesterRef semesterRef;

  private TranscriptStatus status;

  private String fileUrl;

  private Instant requestedAt;
  private Instant generatedAt;
  private Instant sentAt;
  private String errorMessage;
}
