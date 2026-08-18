package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.service.GroupService;
import com.exam.hei.service.PromotionService;
import com.exam.hei.service.StudentGroupAssignmentService;
import com.exam.hei.service.StudentService;
import com.exam.hei.service.StudentTrackChoiceService;
import com.exam.hei.service.TrackService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Students, their accounts, and the two decisions that follow: which group they sit in and which
 * track they follow.
 *
 * <p>Calls the services directly rather than its own REST endpoints, as the graduates page already
 * does: the data is one method call away and going back out over HTTP would only add a round trip
 * and a second authentication to cross.
 *
 * <p>Business failures are caught and re-rendered instead of bubbling up. Left alone they would
 * reach {@code RestExceptionHandler} and answer JSON, which is right for an API client and useless
 * to someone looking at a form. Same reasoning as the sign-in page, which already does this for a
 * wrong password.
 */
@Controller
@AllArgsConstructor
public class StudentAdminPageController {

  private final StudentService studentService;
  private final PromotionService promotionService;
  private final GroupService groupService;
  private final TrackService trackService;
  private final StudentGroupAssignmentService assignmentService;
  private final StudentTrackChoiceService trackChoiceService;
  private final CurrentUserModel currentUserModel;

  /**
   * A duplicate reference or email reaches the database rather than a business check, so it comes
   * back as a constraint violation whose message names columns and constraints. Replaced here: an
   * administrator needs to know what to change, not how the schema is built.
   */
  private static final String TAKEN = "This reference or email is already taken";

  @GetMapping("/ui/admin/students")
  public String students(
      @RequestParam(name = "promotion_id", required = false) UUID promotionId,
      @RequestParam(name = UiFeedback.PARAM, required = false) String done,
      Model model) {
    render(model, promotionId);
    UiFeedback.addTo(model, done);
    return "admin-students";
  }

  @PostMapping("/ui/admin/students")
  public String create(
      @RequestParam String ref,
      @RequestParam(name = "first_name") String firstName,
      @RequestParam(name = "last_name") String lastName,
      @RequestParam String email,
      @RequestParam String password,
      @RequestParam(name = "entrance_date") String entranceDate,
      @RequestParam(name = "promotion_id") UUID promotionId,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      studentService.saveAll(
          List.of(
              Student.builder()
                  .ref(ref)
                  .firstName(firstName)
                  .lastName(lastName)
                  .email(email)
                  .password(password)
                  .entranceDate(LocalDate.parse(entranceDate))
                  .promotion(Promotion.builder().id(promotionId).build())
                  .build()));
      return saved(redirectAttributes, UiFeedback.STUDENT_CREATED, promotionId);
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage(), promotionId);
    } catch (DataIntegrityViolationException e) {
      return failed(model, TAKEN, promotionId);
    }
  }

  @PostMapping("/ui/admin/students/{id}/group")
  public String changeGroup(
      @PathVariable UUID id,
      @RequestParam(name = "group_id") UUID groupId,
      @RequestParam(name = "start_date") String startDate,
      @RequestParam(required = false) String reason,
      @RequestParam(name = "promotion_id") UUID promotionId,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      assignmentService.changeGroup(id, groupId, LocalDate.parse(startDate), reason);
      return saved(redirectAttributes, UiFeedback.GROUP_ASSIGNED, promotionId);
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage(), promotionId);
    } catch (DataIntegrityViolationException e) {
      return failed(model, TAKEN, promotionId);
    }
  }

  @PostMapping("/ui/admin/students/{id}/track")
  public String chooseTrack(
      @PathVariable UUID id,
      @RequestParam(name = "track_id") UUID trackId,
      @RequestParam(name = "from_semester_ref") SemesterRef fromSemesterRef,
      @RequestParam(required = false) String reason,
      @RequestParam(name = "promotion_id") UUID promotionId,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      trackChoiceService.choose(id, trackId, fromSemesterRef, reason);
      return saved(redirectAttributes, UiFeedback.TRACK_CHOSEN, promotionId);
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage(), promotionId);
    } catch (DataIntegrityViolationException e) {
      return failed(model, TAKEN, promotionId);
    }
  }

  /** A flash attribute rather than a query parameter: the message survives one redirect only. */
  private String saved(RedirectAttributes redirectAttributes, String key, UUID promotionId) {
    redirectAttributes.addAttribute(UiFeedback.PARAM, key);
    return "redirect:/ui/admin/students?promotion_id=" + promotionId;
  }

  private String failed(Model model, String message, UUID promotionId) {
    model.addAttribute("error", message);
    render(model, promotionId);
    return "admin-students";
  }

  /**
   * Also runs from the failure path, where the promotion that just failed may be the very thing
   * that is unknown. Rebuilding the page must not raise the same error again, or the form the
   * visitor is meant to correct never reaches them.
   */
  private void render(Model model, UUID promotionId) {
    currentUserModel.addTo(model);
    model.addAttribute("promotions", promotionService.findAll(1, Pagination.MAX_PAGE_SIZE));
    model.addAttribute("tracks", trackService.findAll());
    model.addAttribute("semesters", SemesterRef.values());

    if (promotionId == null) {
      return;
    }
    try {
      model.addAttribute("promotion", promotionService.findById(promotionId));
      model.addAttribute("groups", groupService.findAllByPromotionId(promotionId));
      model.addAttribute(
          "students",
          studentService.findAll(1, Pagination.MAX_PAGE_SIZE, promotionId, null, null, null));
    } catch (NotFoundException e) {
      if (!model.containsAttribute("error")) {
        model.addAttribute("error", e.getMessage());
      }
    }
  }
}
