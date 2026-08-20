package com.exam.hei.endpoint.ui;

import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.service.StudentService;
import com.exam.hei.service.TranscriptService;
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
public class StudentTranscriptPageController {

  private final TranscriptService transcriptService;
  private final StudentService studentService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/students/{id}/transcripts")
  public String transcripts(
      @PathVariable UUID id,
      @RequestParam(name = UiFeedback.PARAM, required = false) String done,
      Model model) {
    render(model, id);
    UiFeedback.addTo(model, done);
    return "admin-student-transcripts";
  }

  @PostMapping("/ui/admin/students/{id}/transcripts")
  public String requestTranscript(
      @PathVariable UUID id,
      @RequestParam(name = "semester_ref", required = false) String semesterRef,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      transcriptService.request(id, scopeOf(semesterRef));
      redirectAttributes.addAttribute(
          UiFeedback.PARAM, UiFeedback.TRANSCRIPT_REQUESTED_FOR_STUDENT);
      return "redirect:/ui/admin/students/" + id + "/transcripts";
    } catch (BadRequestException | ConflictException | NotFoundException | ForbiddenException e) {
      model.addAttribute("error", e.getMessage());
      render(model, id);
      return "admin-student-transcripts";
    }
  }

  private static SemesterRef scopeOf(String semesterRef) {
    return semesterRef == null || semesterRef.isBlank() ? null : SemesterRef.valueOf(semesterRef);
  }

  private void render(Model model, UUID id) {
    currentUserModel.addTo(model);
    model.addAttribute("semesters", SemesterRef.values());
    try {
      model.addAttribute("student", studentService.findById(id));
      model.addAttribute("transcripts", transcriptService.findAllByStudentId(id));
    } catch (NotFoundException e) {
      if (!model.containsAttribute("error")) {
        model.addAttribute("error", e.getMessage());
      }
    }
  }
}
