package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.service.GraduateService;
import com.exam.hei.service.PromotionService;
import com.exam.hei.service.TrackService;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@AllArgsConstructor
public class GraduatePageController {
  private final GraduateService graduateService;
  private final PromotionService promotionService;
  private final TrackService trackService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/graduates")
  public String graduates(
      @RequestParam(name = "promotion_id", required = false) UUID promotionId,
      @RequestParam(name = "track", required = false) String track,
      Model model) {
    currentUserModel.addTo(model);
    model.addAttribute("promotions", promotionService.findAll(1, Pagination.MAX_PAGE_SIZE));
    model.addAttribute("tracks", trackService.findAll());
    model.addAttribute("selectedTrack", track);

    if (promotionId == null) {
      return "graduates";
    }

    model.addAttribute("promotion", promotionService.findById(promotionId));
    model.addAttribute("graduates", graduateService.graduatesOf(promotionId, track));
    return "graduates";
  }
}
