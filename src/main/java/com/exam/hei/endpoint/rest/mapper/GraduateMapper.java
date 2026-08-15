package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Graduate;
import org.springframework.stereotype.Component;

@Component
public class GraduateMapper {

  public Graduate toRest(com.exam.hei.model.Graduate domain) {
    return Graduate.builder()
        .rank(domain.rank())
        .std(domain.std())
        .lastName(domain.lastName())
        .firstName(domain.firstName())
        .generalAverage(domain.generalAverage())
        .trackCode(domain.track() == null ? null : domain.track().getCode())
        .build();
  }
}
