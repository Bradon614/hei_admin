package com.exam.hei.endpoint.ui;

import java.util.Map;
import org.springframework.ui.Model;

final class UiFeedback {

  static final String PARAM = "done";

  static final String STUDENT_CREATED = "student-created";
  static final String GROUP_ASSIGNED = "group-assigned";
  static final String TRACK_CHOSEN = "track-chosen";
  static final String TEACHER_CREATED = "teacher-created";
  static final String ASSIGNMENT_CREATED = "assignment-created";
  static final String GROUP_CREATED = "group-created";
  static final String COURSE_CREATED = "course-created";
  static final String EXAM_CREATED = "exam-created";
  static final String GRADE_SAVED = "grade-saved";
  static final String PROMOTION_CREATED = "promotion-created";
  static final String TRANSCRIPT_REQUESTED = "transcript-requested";
  static final String ACCOUNT_DISABLED = "account-disabled";
  static final String ACCOUNT_ENABLED = "account-enabled";

  private static final Map<String, String> MESSAGES =
      Map.ofEntries(
          Map.entry(STUDENT_CREATED, "Student created, along with their account"),
          Map.entry(GROUP_ASSIGNED, "Group assignment recorded"),
          Map.entry(TRACK_CHOSEN, "Track choice recorded"),
          Map.entry(TEACHER_CREATED, "Teacher created, along with their account"),
          Map.entry(ASSIGNMENT_CREATED, "Teaching assignment recorded"),
          Map.entry(GROUP_CREATED, "Group created"),
          Map.entry(COURSE_CREATED, "Course created"),
          Map.entry(EXAM_CREATED, "Exam created"),
          Map.entry(GRADE_SAVED, "Grade recorded, and added to the change history"),
          Map.entry(PROMOTION_CREATED, "Promotion created"),
          Map.entry(ACCOUNT_DISABLED, "Account disabled. Its holder is signed out at once."),
          Map.entry(ACCOUNT_ENABLED, "Account enabled again"),
          Map.entry(
              TRANSCRIPT_REQUESTED,
              "Transcript requested. It is generated and stored, then emailed to you."));

  private UiFeedback() {}

  static void addTo(Model model, String key) {
    if (key == null) {
      return;
    }
    var message = MESSAGES.get(key);
    if (message != null) {
      model.addAttribute("success", message);
    }
  }
}
