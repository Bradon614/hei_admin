package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.StudentGroupAssignment;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StudentGroupAssignmentMapper {
  private final GroupMapper groupMapper;

  public StudentGroupAssignment toRest(
      com.exam.hei.repository.model.StudentGroupAssignment domain) {
    return StudentGroupAssignment.builder()
        .id(domain.getId())
        .studentId(domain.getStudent() == null ? null : domain.getStudent().getId())
        .group(groupMapper.toRest(domain.getGroup()))
        .startDate(domain.getStartDate())
        .endDate(domain.getEndDate())
        .reason(domain.getReason())
        .build();
  }
}
