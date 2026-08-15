package com.exam.hei.endpoint.rest.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Result of a student across the three years, as exposed by the API. Read only, entirely computed:
 * nothing behind this response is persisted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StudentResult {

  private Student student;

  /** Six elements, from S1 to S6. */
  private List<SemesterResult> semesterResults;

  /** Out of 180. */
  private int totalObtainedCredits;

  private BigDecimal generalAverage;
  private boolean graduated;

  /** Empty if and only if {@code graduated} is true. */
  private List<GraduationBlocker> blockers;
}
