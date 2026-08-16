package com.exam.hei.endpoint.rest.model;

import com.exam.hei.model.SemesterResultStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
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
public class SemesterResult {
  private Semester semester;
  private SemesterResultStatus status;

  private Track track;

  private int obtainedCredits;
  private int requiredCredits;

  private boolean validated;

  private List<CourseResult> courseResults;
}
