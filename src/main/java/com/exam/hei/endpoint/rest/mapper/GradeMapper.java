package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Grade;
import com.exam.hei.endpoint.rest.model.GradeChange;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/** Pure conversion between the REST and the persistence representations of a grade. */
@Component
@AllArgsConstructor
public class GradeMapper {

  private final ExamMapper examMapper;

  public Grade toRest(com.exam.hei.repository.model.Grade domain) {
    return Grade.builder()
        .id(domain.getId())
        .studentId(domain.getStudent().getId())
        .exam(domain.getExam() == null ? null : examMapper.toRest(domain.getExam()))
        .value(domain.getValue())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  /** No domain equivalent: {@code GradeService} turns this straight into a write, not an entity. */
  public com.exam.hei.model.GradeChange toDomain(GradeChange rest) {
    return new com.exam.hei.model.GradeChange(
        rest.getStudentId(), rest.getValue(), rest.getReasonType(), rest.getReason());
  }
}
