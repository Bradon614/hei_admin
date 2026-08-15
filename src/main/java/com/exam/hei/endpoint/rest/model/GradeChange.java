package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.GradeChangeReasonType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entry or update of a grade, as sent to {@code PUT /exams/{id}/grades}.
 *
 * <p>{@code reason_type} and {@code reason} are mandatory: an update without a reason is refused,
 * both here and at the database level.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GradeChange {

  private UUID studentId;
  private BigDecimal value;
  private GradeChangeReasonType reasonType;
  private String reason;
}
