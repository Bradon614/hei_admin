package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Exam;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ExamMapper {

  private final CourseMapper courseMapper;

  public Exam toRest(com.exam.hei.repository.model.Exam domain) {
    return Exam.builder()
        .id(domain.getId())
        .title(domain.getTitle())
        .dateExam(domain.getDateExam())
        .coefficient(domain.getCoefficient())
        .course(domain.getCourse() == null ? null : courseMapper.toRest(domain.getCourse()))
        .build();
  }

  /** The course is not read from the payload: it comes from the path, and never changes. */
  public com.exam.hei.repository.model.Exam toDomain(Exam rest) {
    return com.exam.hei.repository.model.Exam.builder()
        .id(rest.getId())
        .title(rest.getTitle())
        .dateExam(rest.getDateExam())
        .coefficient(rest.getCoefficient())
        .build();
  }
}
