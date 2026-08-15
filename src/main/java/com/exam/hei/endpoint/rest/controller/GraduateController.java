package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.GraduateMapper;
import com.exam.hei.endpoint.rest.model.Graduate;
import com.exam.hei.service.GraduateService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of the graduate list. Role restrictions live in SecurityConf. */
@RestController
@AllArgsConstructor
public class GraduateController {

  private final GraduateService graduateService;
  private final GraduateMapper graduateMapper;

  @GetMapping("/promotions/{promotionId}/graduates")
  public List<Graduate> getPromotionGraduates(@PathVariable UUID promotionId) {
    return graduateService.graduatesOf(promotionId).stream().map(graduateMapper::toRest).toList();
  }
}
