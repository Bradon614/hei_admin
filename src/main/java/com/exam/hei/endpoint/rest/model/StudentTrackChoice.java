package com.exam.hei.endpoint.rest.model;

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
public class StudentTrackChoice {

  private UUID id;
  private UUID studentId;
  private Track track;

  /**
   * First semester this track applies to, and every later one unless a new choice supersedes it.
   */
  private Semester fromSemester;

  private Instant decidedAt;
  private String reason;
}
