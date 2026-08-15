package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.service.GraduateService;
import com.exam.hei.service.PromotionService;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The graduate list, rendered server side.
 *
 * <p>Calls the services directly rather than its own REST endpoints over HTTP: the data is already
 * one method call away, and going back out through the network would only add a round trip and a
 * second authentication to cross.
 */
@Controller
@AllArgsConstructor
public class GraduatePageController {

  private final GraduateService graduateService;
  private final PromotionService promotionService;

  @GetMapping("/ui/graduates")
  public String graduates(
      @RequestParam(name = "promotion_id", required = false) UUID promotionId, Model model) {
    model.addAttribute("promotions", promotionService.findAll(1, Pagination.MAX_PAGE_SIZE));

    // No promotion chosen yet: render the picker alone rather than guessing one for the user.
    if (promotionId == null) {
      return "graduates";
    }

    model.addAttribute("promotion", promotionService.findById(promotionId));
    model.addAttribute("graduates", graduateService.graduatesOf(promotionId));
    return "graduates";
  }
}
