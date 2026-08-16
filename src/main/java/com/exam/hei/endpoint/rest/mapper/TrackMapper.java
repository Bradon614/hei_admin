package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Track;
import org.springframework.stereotype.Component;

@Component
public class TrackMapper {
  public Track toRest(com.exam.hei.repository.model.Track domain) {
    return Track.builder().id(domain.getId()).code(domain.getCode()).name(domain.getName()).build();
  }

  public com.exam.hei.repository.model.Track toDomain(Track rest) {
    return com.exam.hei.repository.model.Track.builder()
        .id(rest.getId())
        .code(rest.getCode())
        .name(rest.getName())
        .build();
  }
}
