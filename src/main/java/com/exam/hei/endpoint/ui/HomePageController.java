package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.service.GradeService;
import com.exam.hei.service.ResultService;
import com.exam.hei.service.StudentCourseService;
import com.exam.hei.service.TeachingAssignmentService;
import com.exam.hei.service.TranscriptService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The page every signed-in visitor lands on, filled according to who they are.
 *
 * <p>The caller's own identifier comes from the token, never from the URL, so there is no id to
 * tamper with. Beyond that the screen enforces nothing: the services it calls run {@code
 * StudentAuthorizer} and {@code GradeAuthorizer} themselves, which is what makes the separation
 * real rather than a matter of which fragment gets rendered.
 */
@Controller
@AllArgsConstructor
public class HomePageController {

  private final CurrentUserModel currentUserModel;
  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final StudentCourseService studentCourseService;
  private final GradeService gradeService;
  private final ResultService resultService;
  private final TranscriptService transcriptService;
  private final TeachingAssignmentService teachingAssignmentService;

  @GetMapping("/ui/admin")
  public String admin(Model model) {
    currentUserModel.addTo(model);
    return "admin";
  }

  @GetMapping("/ui/me")
  public String me(
      @RequestParam(name = UiFeedback.PARAM, required = false) String done, Model model) {
    render(model);
    UiFeedback.addTo(model, done);
    return "me";
  }

  @PostMapping("/ui/me/transcripts")
  public String requestTranscript(
      @RequestParam(name = "semester_ref", required = false) String semesterRef,
      Model model,
      RedirectAttributes redirectAttributes) {
    var studentId = authenticatedResourceProvider.getAuthenticatedStudentId();
    if (studentId.isEmpty()) {
      model.addAttribute("error", "Only a student can ask for their own transcript");
      render(model);
      return "me";
    }
    try {
      transcriptService.request(
          studentId.get(),
          semesterRef == null || semesterRef.isBlank() ? null : SemesterRef.valueOf(semesterRef));
      redirectAttributes.addAttribute(UiFeedback.PARAM, UiFeedback.TRANSCRIPT_REQUESTED);
      return "redirect:/ui/me";
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      model.addAttribute("error", e.getMessage());
      render(model);
      return "me";
    }
  }

  private void render(Model model) {
    currentUserModel.addTo(model);
    model.addAttribute("semesters", SemesterRef.values());
    authenticatedResourceProvider.getAuthenticatedStudentId().ifPresent(id -> asStudent(model, id));
    authenticatedResourceProvider.getAuthenticatedTeacherId().ifPresent(id -> asTeacher(model, id));
  }

  private void asStudent(Model model, java.util.UUID studentId) {
    model.addAttribute("courses", studentCourseService.findApplicable(studentId, null));
    model.addAttribute("grades", gradeService.findAllByStudentId(studentId, null));
    model.addAttribute("result", resultService.resultOf(studentId));
    model.addAttribute("transcripts", transcriptService.findAllByStudentId(studentId));
  }

  private void asTeacher(Model model, java.util.UUID teacherId) {
    model.addAttribute("assignments", teachingAssignmentService.findAllByTeacherId(teacherId));
  }
}
