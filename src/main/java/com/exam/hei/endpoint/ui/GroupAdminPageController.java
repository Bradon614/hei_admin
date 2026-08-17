package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Track;
import com.exam.hei.service.GroupService;
import com.exam.hei.service.PromotionService;
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
 * Teaching groups, listed and created one promotion at a time.
 *
 * <p>The track is optional: a group without one stays mixed, which is what the common core needs.
 * Whether a chosen track is one the promotion actually opens is decided by {@code GroupService},
 * not here — this screen only reports what it answers.
 */
@Controller
@AllArgsConstructor
public class GroupAdminPageController {

  private final GroupService groupService;
  private final PromotionService promotionService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/groups")
  public String groups(
      @RequestParam(name = "promotion_id", required = false) UUID promotionId, Model model) {
    render(model, promotionId);
    return "admin-groups";
  }

  @PostMapping("/ui/admin/groups")
  public String create(
      @RequestParam String ref,
      @RequestParam(name = "track_id", required = false) String trackId,
      @RequestParam(name = "promotion_id") UUID promotionId,
      Model model) {
    try {
      var group = Group.builder().ref(ref);
      if (trackId != null && !trackId.isBlank()) {
        group.track(Track.builder().id(UUID.fromString(trackId)).build());
      }
      groupService.saveAll(promotionId, List.of(group.build()));
      return "redirect:/ui/admin/groups?promotion_id=" + promotionId;
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage(), promotionId);
    } catch (DataIntegrityViolationException e) {
      return failed(model, "This promotion already has a group with that reference", promotionId);
    }
  }

  private String failed(Model model, String message, UUID promotionId) {
    model.addAttribute("error", message);
    render(model, promotionId);
    return "admin-groups";
  }

  private void render(Model model, UUID promotionId) {
    currentUserModel.addTo(model);
    model.addAttribute("promotions", promotionService.findAll(1, Pagination.MAX_PAGE_SIZE));

    if (promotionId == null) {
      return;
    }
    try {
      var promotion = promotionService.findById(promotionId);
      model.addAttribute("promotion", promotion);
      // Only the tracks this promotion opens: offering the others would invite a rejection the
      // form could have avoided asking for.
      model.addAttribute("tracks", promotion.getTracks());
      model.addAttribute("groups", groupService.findAllByPromotionId(promotionId));
    } catch (NotFoundException e) {
      if (!model.containsAttribute("error")) {
        model.addAttribute("error", e.getMessage());
      }
    }
  }
}
