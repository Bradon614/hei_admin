package com.exam.hei.endpoint.ui;

import com.exam.hei.model.GradeChange;
import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.GradeHistory;
import com.exam.hei.service.ExamService;
import com.exam.hei.service.GradeService;
import com.exam.hei.service.PromotionService;
import com.exam.hei.service.StudentService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@AllArgsConstructor
public class GradeAdminPageController {

  private final GradeService gradeService;
  private final ExamService examService;
  private final StudentService studentService;
  private final PromotionService promotionService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/exams/{examId}/grades")
  public String grades(
      @PathVariable UUID examId,
      @RequestParam(name = "promotion_id", required = false) UUID promotionId,
      @RequestParam(name = UiFeedback.PARAM, required = false) String done,
      Model model) {
    render(model, examId, promotionId);
    UiFeedback.addTo(model, done);
    return "admin-grades";
  }

  @PostMapping("/ui/admin/exams/{examId}/grades")
  public String save(
      @PathVariable UUID examId,
      @RequestParam(name = "student_id") UUID studentId,
      @RequestParam BigDecimal value,
      @RequestParam(name = "reason_type") GradeChangeReasonType reasonType,
      @RequestParam String reason,
      @RequestParam(name = "promotion_id", required = false) UUID promotionId,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      gradeService.crupdate(examId, List.of(new GradeChange(studentId, value, reasonType, reason)));
      redirectAttributes.addAttribute(UiFeedback.PARAM, UiFeedback.GRADE_SAVED);
      return "redirect:/ui/admin/exams/"
          + examId
          + "/grades"
          + (promotionId == null ? "" : "?promotion_id=" + promotionId);
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      model.addAttribute("error", e.getMessage());
      render(model, examId, promotionId);
      return "admin-grades";
    }
  }

  private void render(Model model, UUID examId, UUID promotionId) {
    currentUserModel.addTo(model);
    model.addAttribute("promotions", promotionService.findAll(1, Pagination.MAX_PAGE_SIZE));
    model.addAttribute("correctionReasons", correctionReasons());

    try {
      model.addAttribute("exam", examService.findById(examId));
      var grades = gradeService.findAllByExamId(examId);
      model.addAttribute("gradesByStudent", byStudent(grades));
      model.addAttribute("history", historyOf(grades));
    } catch (NotFoundException e) {
      if (!model.containsAttribute("error")) {
        model.addAttribute("error", e.getMessage());
      }
      return;
    }

    if (promotionId == null) {
      return;
    }
    try {
      model.addAttribute("promotion", promotionService.findById(promotionId));
      model.addAttribute(
          "students",
          studentService.findAll(1, Pagination.MAX_PAGE_SIZE, promotionId, null, null, null));
    } catch (NotFoundException e) {
      if (!model.containsAttribute("error")) {
        model.addAttribute("error", e.getMessage());
      }
    }
  }

  private static List<GradeChangeReasonType> correctionReasons() {
    return List.of(
        GradeChangeReasonType.CORRECTION,
        GradeChangeReasonType.CLAIM,
        GradeChangeReasonType.INPUT_ERROR,
        GradeChangeReasonType.OTHER);
  }

  private static Map<UUID, Grade> byStudent(List<Grade> grades) {
    Map<UUID, Grade> byStudent = new LinkedHashMap<>();
    grades.forEach(grade -> byStudent.put(grade.getStudent().getId(), grade));
    return byStudent;
  }

  private List<GradeHistory> historyOf(List<Grade> grades) {
    var history = new ArrayList<GradeHistory>();
    for (var grade : grades) {
      history.addAll(gradeService.findHistoryOf(grade.getId()));
    }
    history.sort(Comparator.comparing(GradeHistory::getChangedAt).reversed());
    return history;
  }
}
