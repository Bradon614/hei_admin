package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.GroupMapper;
import com.exam.hei.endpoint.rest.model.Group;
import com.exam.hei.service.GroupService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of groups. Role restrictions live in SecurityConf. */
@RestController
@AllArgsConstructor
public class GroupController {

  private final GroupService groupService;
  private final GroupMapper groupMapper;

  @GetMapping("/promotions/{promotionId}/groups")
  public List<Group> getPromotionGroups(@PathVariable UUID promotionId) {
    return groupService.findAllByPromotionId(promotionId).stream()
        .map(groupMapper::toRest)
        .toList();
  }

  @PutMapping("/promotions/{promotionId}/groups")
  public List<Group> crupdatePromotionGroups(
      @PathVariable UUID promotionId, @RequestBody List<Group> groups) {
    var saved =
        groupService.saveAll(promotionId, groups.stream().map(groupMapper::toDomain).toList());
    return saved.stream().map(groupMapper::toRest).toList();
  }
}
