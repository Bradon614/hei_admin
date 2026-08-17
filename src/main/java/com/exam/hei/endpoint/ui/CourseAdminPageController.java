package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Track;
import com.exam.hei.service.CourseService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The catalogue: every course, and the form that adds one.
 *
 * <p>The track field is offered on every semester because the form cannot know which ones are
 * common core without asking; {@code CourseService} refuses the combination and the refusal is what
 * this screen shows.
 */
@Controller
@AllArgsConstructor
public class CourseAdminPageController {

  private final CourseService courseService;
  private final TrackService trackService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/courses")
  public String courses(
      @RequestParam(name = "semester_ref", required = false) String semesterRef,
      @RequestParam(name = UiFeedback.PARAM, required = false) String done,
      Model model) {
    render(model, semesterRef);
    UiFeedback.addTo(model, done);
    return "admin-courses";
  }

  @PostMapping("/ui/admin/courses")
  public String create(
      @RequestParam String ref,
      @RequestParam String title,
      @RequestParam int credits,
      @RequestParam(name = "semester_ref") SemesterRef semesterRef,
      @RequestParam(name = "track_id", required = false) String trackId,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      var course =
          Course.builder()
              .ref(ref)
              .title(title)
              .credits(credits)
              .semester(Semester.builder().ref(semesterRef).build());
      if (trackId != null && !trackId.isBlank()) {
        course.track(Track.builder().id(UUID.fromString(trackId)).build());
      }
      courseService.saveAll(List.of(course.build()));
      redirectAttributes.addAttribute(UiFeedback.PARAM, UiFeedback.COURSE_CREATED);
      return "redirect:/ui/admin/courses?semester_ref=" + semesterRef;
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage(), semesterRef.name());
    } catch (DataIntegrityViolationException e) {
      return failed(model, "A course already carries that reference", semesterRef.name());
    }
  }

  private String failed(Model model, String message, String semesterRef) {
    model.addAttribute("error", message);
    render(model, semesterRef);
    return "admin-courses";
  }

  private void render(Model model, String semesterRef) {
    currentUserModel.addTo(model);
    model.addAttribute("semesters", SemesterRef.values());
    model.addAttribute("tracks", trackService.findAll());
    model.addAttribute("selectedSemester", semesterRef);
    model.addAttribute(
        "courses", courseService.findAll(1, Pagination.MAX_PAGE_SIZE, asRef(semesterRef), null));
  }

  private SemesterRef asRef(String semesterRef) {
    if (semesterRef == null || semesterRef.isBlank()) {
      return null;
    }
    try {
      return SemesterRef.valueOf(semesterRef);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
