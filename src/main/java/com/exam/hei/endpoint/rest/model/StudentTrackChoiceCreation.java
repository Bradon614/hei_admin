package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.SemesterRef;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StudentTrackChoiceCreation {

  private UUID trackId;

  /** A first choice takes effect on the first semester that expects a track. */
  private SemesterRef fromSemesterRef;

  private String reason;
}
