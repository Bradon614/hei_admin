package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.GradeHistory;
import org.springframework.stereotype.Component;

/** Pure conversion between the REST and the persistence representations of a grade history row. */
@Component
public class GradeHistoryMapper {

  public GradeHistory toRest(com.exam.hei.repository.model.GradeHistory domain) {
    return GradeHistory.builder()
        .id(domain.getId())
        .gradeId(domain.getGrade().getId())
        .oldValue(domain.getOldValue())
        .newValue(domain.getNewValue())
        .reasonType(domain.getReasonType())
        .reason(domain.getReason())
        .changedBy(domain.getChangedBy().getId())
        .changedByEmail(domain.getChangedBy().getEmail())
        .changedAt(domain.getChangedAt())
        .build();
  }
}
