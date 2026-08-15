package com.exam.hei.endpoint.rest.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Matches exactly the columns of the Excel export: rank, STD, last name, first name, general
 * average. {@code trackCode} is added beyond what the assignment requires.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Graduate {

  private int rank;
  private String std;
  private String lastName;
  private String firstName;
  private BigDecimal generalAverage;

  /** Exit track of the diploma. Null only in theory: graduating requires a track choice. */
  private String trackCode;
}
