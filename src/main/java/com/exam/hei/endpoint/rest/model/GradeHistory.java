package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.GradeChangeReasonType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Read only, never updated, never deleted. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GradeHistory {

  private UUID id;
  private UUID gradeId;

  /** Null on the first entry. */
  private BigDecimal oldValue;

  private BigDecimal newValue;
  private GradeChangeReasonType reasonType;
  private String reason;

  /** Account that made the modification. */
  private UUID changedBy;

  private String changedByEmail;
  private Instant changedAt;
}
