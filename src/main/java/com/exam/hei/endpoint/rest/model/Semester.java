package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.SemesterRef;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** One of the six semesters of the curriculum, as exposed by the API. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Semester {

  private UUID id;
  private SemesterRef ref;
  private Integer semOrder;
  private Integer yearNumber;
  private Integer requiredCredits;

  /** True for the common core semesters, where no track applies. */
  private Boolean commonCore;
}
