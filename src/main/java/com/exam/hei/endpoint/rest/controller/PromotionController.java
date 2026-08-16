package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.PromotionMapper;
import com.exam.hei.endpoint.rest.model.Promotion;
import com.exam.hei.service.PromotionService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class PromotionController {
  private final PromotionService promotionService;
  private final PromotionMapper promotionMapper;

  @GetMapping("/promotions")
  public List<Promotion> getPromotions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "50") int pageSize) {
    return promotionService.findAll(page, pageSize).stream().map(promotionMapper::toRest).toList();
  }

  @GetMapping("/promotions/{id}")
  public Promotion getPromotionById(@PathVariable UUID id) {
    return promotionMapper.toRest(promotionService.findById(id));
  }

  @PutMapping("/promotions")
  public List<Promotion> crupdatePromotions(@RequestBody List<Promotion> promotions) {
    var saved =
        promotionService.saveAll(promotions.stream().map(promotionMapper::toDomain).toList());
    return saved.stream().map(promotionMapper::toRest).toList();
  }
}
