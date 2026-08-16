package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Group;
import com.exam.hei.repository.model.Track;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GroupMapper {
  private final PromotionMapper promotionMapper;
  private final TrackMapper trackMapper;

  public Group toRest(com.exam.hei.repository.model.Group domain) {
    return Group.builder()
        .id(domain.getId())
        .ref(domain.getRef())
        .promotion(
            domain.getPromotion() == null ? null : promotionMapper.toRest(domain.getPromotion()))
        .track(domain.getTrack() == null ? null : trackMapper.toRest(domain.getTrack()))
        .build();
  }

  public com.exam.hei.repository.model.Group toDomain(Group rest) {
    return com.exam.hei.repository.model.Group.builder()
        .id(rest.getId())
        .ref(rest.getRef())
        .track(trackOf(rest))
        .build();
  }

  private Track trackOf(Group rest) {
    UUID trackId = rest.getTrack() == null ? null : rest.getTrack().getId();
    return trackId == null ? null : Track.builder().id(trackId).build();
  }
}
