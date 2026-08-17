package com.exam.hei.endpoint.ui;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.service.CourseService;
import com.exam.hei.service.ExamService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The exams of one course, which is the only place an exam exists.
 *
 * <p>The form asks for a day; the column stores an instant. Midnight UTC is what the choice
 * resolves to, and it is the same instant {@code GradeService} compares against when it decides
 * which group a student belonged to on exam day.
 */
@Controller
@AllArgsConstructor
public class ExamAdminPageController {

  private final ExamService examService;
  private final CourseService courseService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/courses/{courseId}/exams")
  public String exams(
      @PathVariable UUID courseId,
      @RequestParam(name = UiFeedback.PARAM, required = false) String done,
      Model model) {
    render(model, courseId);
    UiFeedback.addTo(model, done);
    return "admin-exams";
  }

  @PostMapping("/ui/admin/courses/{courseId}/exams")
  public String create(
      @PathVariable UUID courseId,
      @RequestParam String title,
      @RequestParam(name = "date_exam") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateExam,
      @RequestParam BigDecimal coefficient,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      examService.saveAll(
          courseId,
          List.of(
              Exam.builder()
                  .title(title)
                  .dateExam(dateExam.atStartOfDay(ZoneOffset.UTC).toInstant())
                  .coefficient(coefficient)
                  .build()));
      redirectAttributes.addAttribute(UiFeedback.PARAM, UiFeedback.EXAM_CREATED);
      return "redirect:/ui/admin/courses/" + courseId + "/exams";
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      model.addAttribute("error", e.getMessage());
      render(model, courseId);
      return "admin-exams";
    }
  }

  private void render(Model model, UUID courseId) {
    currentUserModel.addTo(model);
    try {
      model.addAttribute("course", courseService.findById(courseId));
      model.addAttribute("exams", examService.findAllByCourseId(courseId));
    } catch (NotFoundException e) {
      if (!model.containsAttribute("error")) {
        model.addAttribute("error", e.getMessage());
      }
    }
  }
}
