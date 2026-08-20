package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.service.PromotionService;
import com.exam.hei.service.ResultService;
import com.exam.hei.service.TrackService;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@AllArgsConstructor
public class PromotionResultPageController {

  private final ResultService resultService;
  private final PromotionService promotionService;
  private final TrackService trackService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/promotions/{promotionId}/results")
  public String results(
      @PathVariable UUID promotionId,
      @RequestParam(name = "track_code", required = false) String trackCode,
      Model model) {
    currentUserModel.addTo(model);
    model.addAttribute("tracks", trackService.findAll());
    model.addAttribute("selectedTrack", trackCode);

    var track = trackCode == null || trackCode.isBlank() ? null : trackCode;
    try {
      model.addAttribute("promotion", promotionService.findById(promotionId));
      model.addAttribute(
          "results",
          resultService.resultsOfPromotion(promotionId, track, 1, Pagination.MAX_PAGE_SIZE));
    } catch (NotFoundException e) {
      model.addAttribute("error", e.getMessage());
    }
    return "admin-results";
  }
}
