package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Grade;
import com.exam.hei.endpoint.rest.model.GradeChange;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

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

  public com.exam.hei.model.GradeChange toDomain(GradeChange rest) {
    return new com.exam.hei.model.GradeChange(
        rest.getStudentId(), rest.getValue(), rest.getReasonType(), rest.getReason());
  }
}
