package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Promotion;
import com.exam.hei.endpoint.rest.model.Track;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class PromotionMapper {
  private final TrackMapper trackMapper;

  public Promotion toRest(com.exam.hei.repository.model.Promotion domain) {
    return Promotion.builder()
        .id(domain.getId())
        .ref(domain.getRef())
        .name(domain.getName())
        .startYear(domain.getStartYear())
        .endYear(domain.getEndYear())
        .tracks(domain.getTracks().stream().map(trackMapper::toRest).toList())
        .build();
  }

  public com.exam.hei.repository.model.Promotion toDomain(Promotion rest) {
    return com.exam.hei.repository.model.Promotion.builder()
        .id(rest.getId())
        .ref(rest.getRef())
        .name(rest.getName())
        .startYear(rest.getStartYear() == null ? 0 : rest.getStartYear())
        .endYear(rest.getEndYear() == null ? 0 : rest.getEndYear())
        .tracks(
            (rest.getTracks() == null ? List.<Track>of() : rest.getTracks())
                .stream().map(trackMapper::toDomain).toList())
        .build();
  }
}
