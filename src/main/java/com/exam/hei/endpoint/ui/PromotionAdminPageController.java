package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Track;
import com.exam.hei.service.PromotionService;
import com.exam.hei.service.TrackService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Promotions, and the tracks each of them opens.
 *
 * <p>The tracks are not decoration. {@code GroupService} refuses a group on a track its promotion
 * does not open, and {@code StudentTrackChoiceService} refuses the matching choice, so a promotion
 * created without them can never reach the semesters where tracks begin.
 */
@Controller
@AllArgsConstructor
public class PromotionAdminPageController {

  private static final String REF_TOO_LONG =
      "A promotion reference is at most 5 characters, and must not already be taken";

  private final PromotionService promotionService;
  private final TrackService trackService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/promotions")
  public String promotions(Model model) {
    render(model);
    return "admin-promotions";
  }

  @PostMapping("/ui/admin/promotions")
  public String create(
      @RequestParam String ref,
      @RequestParam String name,
      @RequestParam(name = "start_year") int startYear,
      @RequestParam(name = "end_year") int endYear,
      @RequestParam(name = "track_ids", required = false) List<UUID> trackIds,
      Model model) {
    try {
      promotionService.saveAll(
          List.of(
              Promotion.builder()
                  .ref(ref)
                  .name(name)
                  .startYear(startYear)
                  .endYear(endYear)
                  .tracks(openedTracks(trackIds))
                  .build()));
      return "redirect:/ui/admin/promotions";
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage());
    } catch (DataIntegrityViolationException e) {
      return failed(model, REF_TOO_LONG);
    }
  }

  private static List<Track> openedTracks(List<UUID> trackIds) {
    if (trackIds == null) {
      return List.of();
    }
    return trackIds.stream().map(id -> Track.builder().id(id).build()).toList();
  }

  private String failed(Model model, String message) {
    model.addAttribute("error", message);
    render(model);
    return "admin-promotions";
  }

  private void render(Model model) {
    currentUserModel.addTo(model);
    model.addAttribute("promotions", promotionService.findAll(1, Pagination.MAX_PAGE_SIZE));
    model.addAttribute("tracks", trackService.findAll());
  }
}
