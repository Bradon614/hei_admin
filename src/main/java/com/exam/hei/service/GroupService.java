package com.exam.hei.service;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.GroupRepository;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class GroupService {
  private final GroupRepository groupRepository;
  private final PromotionService promotionService;
  private final TrackService trackService;

  public List<Group> findAllByPromotionId(UUID promotionId) {
    promotionService.findById(promotionId);
    return groupRepository.findAllByPromotionIdOrderByRefAsc(promotionId);
  }

  public Group findById(UUID id) {
    return groupRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Group " + id + " not found"));
  }

  @Transactional
  public List<Group> saveAll(UUID promotionId, List<Group> groups) {
    var promotion = promotionService.findById(promotionId);
    groups.forEach(group -> attachTo(promotion, group));
    return groupRepository.saveAll(groups);
  }

  private void attachTo(Promotion promotion, Group group) {
    if (group.getId() != null) {
      findById(group.getId());
    }
    group.setPromotion(promotion);
    group.setTrack(resolveTrack(promotion, group));
  }

  private Track resolveTrack(Promotion promotion, Group group) {
    if (group.getTrack() == null || group.getTrack().getId() == null) {
      return null;
    }
    var track = trackService.findById(group.getTrack().getId());
    var opened =
        promotion.getTracks().stream()
            .anyMatch(openedTrack -> openedTrack.getId().equals(track.getId()));
    if (!opened) {
      throw new BadRequestException(
          "Track " + track.getCode() + " is not opened by promotion " + promotion.getRef());
    }
    return track;
  }
}
