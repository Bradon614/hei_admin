package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Semester;
import org.springframework.stereotype.Component;

/** One way only: semesters are reference data, the API never writes them. */
@Component
public class SemesterMapper {

  public Semester toRest(com.exam.hei.repository.model.Semester domain) {
    return Semester.builder()
        .id(domain.getId())
        .ref(domain.getRef())
        .semOrder(domain.getSemOrder())
        .yearNumber(domain.getYearNumber())
        .requiredCredits(domain.getRequiredCredits())
        .commonCore(domain.isCommonCore())
        .build();
  }
}
