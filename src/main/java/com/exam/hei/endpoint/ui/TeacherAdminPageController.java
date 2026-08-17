package com.exam.hei.endpoint.ui;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import com.exam.hei.service.CourseService;
import com.exam.hei.service.GroupService;
import com.exam.hei.service.PromotionService;
import com.exam.hei.service.TeacherService;
import com.exam.hei.service.TeachingAssignmentService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Teachers, their accounts, and what each of them teaches.
 *
 * <p>A teaching assignment binds a course, a teacher and a group at once: the same course given to
 * two groups by two different people is the case three separate lists could not express, so the
 * form asks for all three.
 */
@Controller
@AllArgsConstructor
public class TeacherAdminPageController {

  private static final String TAKEN = "This reference or email is already taken";

  private final TeacherService teacherService;
  private final TeachingAssignmentService teachingAssignmentService;
  private final CourseService courseService;
  private final GroupService groupService;
  private final PromotionService promotionService;
  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin/teachers")
  public String teachers(Model model) {
    render(model);
    return "admin-teachers";
  }

  @PostMapping("/ui/admin/teachers")
  public String create(
      @RequestParam String ref,
      @RequestParam(name = "first_name") String firstName,
      @RequestParam(name = "last_name") String lastName,
      @RequestParam String email,
      @RequestParam String password,
      Model model) {
    try {
      teacherService.saveAll(
          List.of(
              Teacher.builder()
                  .ref(ref)
                  .firstName(firstName)
                  .lastName(lastName)
                  .email(email)
                  .password(password)
                  .build()));
      return "redirect:/ui/admin/teachers";
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage());
    } catch (DataIntegrityViolationException e) {
      return failed(model, TAKEN);
    }
  }

  @PostMapping("/ui/admin/teachers/{id}/assignments")
  public String assign(
      @PathVariable UUID id,
      @RequestParam(name = "course_id") UUID courseId,
      @RequestParam(name = "group_id") UUID groupId,
      Model model) {
    try {
      teachingAssignmentService.saveAll(
          List.of(
              TeachingAssignment.builder()
                  .teacher(Teacher.builder().id(id).build())
                  .course(Course.builder().id(courseId).build())
                  .group(Group.builder().id(groupId).build())
                  .build()));
      return "redirect:/ui/admin/teachers";
    } catch (BadRequestException | ConflictException | NotFoundException e) {
      return failed(model, e.getMessage());
    } catch (DataIntegrityViolationException e) {
      return failed(model, "This teacher already teaches that course to that group");
    }
  }

  private String failed(Model model, String message) {
    model.addAttribute("error", message);
    render(model);
    return "admin-teachers";
  }

  private void render(Model model) {
    currentUserModel.addTo(model);
    var teachers = teacherService.findAll(1, Pagination.MAX_PAGE_SIZE);
    model.addAttribute("teachers", teachers);
    model.addAttribute("courses", courseService.findAll(1, Pagination.MAX_PAGE_SIZE, null, null));
    model.addAttribute("groups", allGroups());

    // Resolved per teacher rather than joined in: a school has few enough of them for that to cost
    // nothing, and the alternative would mean a query this screen is the only caller of.
    Map<UUID, List<TeachingAssignment>> assignments = new LinkedHashMap<>();
    for (var teacher : teachers) {
      assignments.put(
          teacher.getId(), teachingAssignmentService.findAllByTeacherId(teacher.getId()));
    }
    model.addAttribute("assignments", assignments);
  }

  private List<Group> allGroups() {
    var groups = new ArrayList<Group>();
    for (var promotion : promotionService.findAll(1, Pagination.MAX_PAGE_SIZE)) {
      groups.addAll(groupService.findAllByPromotionId(promotion.getId()));
    }
    return groups;
  }
}
