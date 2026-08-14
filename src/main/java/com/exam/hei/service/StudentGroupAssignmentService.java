package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentGroupAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records which group a student belongs to, over time.
 *
 * <p>A student may change group at any moment, several times within the same year or the same
 * semester. Nothing here ever touches a grade: a grade belongs to the student and the exam, never
 * to a group, so moving a student cannot make one disappear.
 */
@Service
@AllArgsConstructor
public class StudentGroupAssignmentService {

  private final StudentGroupAssignmentRepository assignmentRepository;
  private final StudentRepository studentRepository;
  private final GroupService groupService;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentAuthorizer studentAuthorizer;

  /** Read path: answers "which groups did the student go through?". */
  public List<StudentGroupAssignment> findAllByStudentId(UUID studentId) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return assignmentRepository.findAllByStudentIdOrderByStartDateAsc(studentId);
  }

  /** Read path: answers "which group was the student in on that date?". */
  public Optional<StudentGroupAssignment> findActiveAt(UUID studentId, LocalDate date) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return assignmentRepository.findActiveAt(studentId, date);
  }

  /** The group the student belongs to right now, empty if they were never assigned one. */
  public Optional<Group> currentGroupOf(UUID studentId) {
    return assignmentRepository
        .findByStudentIdAndEndDateIsNull(studentId)
        .map(StudentGroupAssignment::getGroup);
  }

  /**
   * Moves a student to another group from a given date.
   *
   * <p>Closes the assignment currently open on the day before, then opens the new one, in a single
   * transaction. A change never alters the track: it is only checked against it.
   */
  @Transactional
  public StudentGroupAssignment changeGroup(
      UUID studentId, UUID groupId, LocalDate startDate, String reason) {
    var student = requireStudent(studentId);
    var group = groupService.findById(groupId);

    checkGroupBelongsToThePromotionOf(student, group);
    checkGroupMatchesTheTrackOf(studentId, group);

    var current = assignmentRepository.findByStudentIdAndEndDateIsNull(studentId);
    checkStartsAfterTheCurrentAssignment(current, startDate);
    checkNoOtherAssignmentCoversThatPeriod(studentId, startDate, current);

    // Flushed, not merely saved: Hibernate would otherwise order the insert of the new assignment
    // before this update, and the exclusion constraint would see two open periods at once.
    current.ifPresent(
        open -> {
          open.setEndDate(startDate.minusDays(1));
          assignmentRepository.saveAndFlush(open);
        });

    return assignmentRepository.save(
        StudentGroupAssignment.builder()
            .student(student)
            .group(group)
            .startDate(startDate)
            .reason(reason)
            .build());
  }

  private Student requireStudent(UUID studentId) {
    return studentRepository
        .findById(studentId)
        .orElseThrow(() -> new NotFoundException("Student " + studentId + " not found"));
  }

  private void checkGroupBelongsToThePromotionOf(Student student, Group group) {
    if (!group.getPromotion().getId().equals(student.getPromotion().getId())) {
      throw new BadRequestException(
          "Group " + group.getRef() + " does not belong to the promotion of the student");
    }
  }

  /**
   * Rules R1 and R2 together.
   *
   * <p>R1: entering a track-bearing group requires having chosen that track. R2: once a track has
   * been chosen, every later assignment targets a group of that track, so going back to a common
   * core group is closed.
   *
   * <p>A group change therefore never changes a track. Moving from EL to TN takes a new track
   * choice, never a reassignment.
   */
  private void checkGroupMatchesTheTrackOf(UUID studentId, Group group) {
    var chosen = trackChoiceService.exitTrackOf(studentId);

    if (chosen.isEmpty()) {
      if (group.getTrack() != null) {
        throw new ConflictException(
            "TRACK_NOT_SELECTED: group "
                + group.getRef()
                + " belongs to track "
                + group.getTrack().getCode()
                + ", which the student has not chosen");
      }
      return;
    }

    if (group.getTrack() == null) {
      throw new ConflictException(
          "The student follows track "
              + chosen.get().getCode()
              + " and cannot go back to the common core group "
              + group.getRef());
    }
    if (!group.getTrack().getId().equals(chosen.get().getId())) {
      throw new ConflictException(
          "The student follows track "
              + chosen.get().getCode()
              + " and cannot join a group of track "
              + group.getTrack().getCode());
    }
  }

  private void checkStartsAfterTheCurrentAssignment(
      Optional<StudentGroupAssignment> current, LocalDate startDate) {
    current.ifPresent(
        open -> {
          if (!startDate.isAfter(open.getStartDate())) {
            throw new BadRequestException(
                "A group change starts after the assignment it closes, which began on "
                    + open.getStartDate());
          }
        });
  }

  /**
   * The assignment about to be closed is expected to overlap, so it is excluded. Anything else
   * overlapping is a genuine clash, reported before the exclusion constraint has to fire.
   */
  private void checkNoOtherAssignmentCoversThatPeriod(
      UUID studentId, LocalDate startDate, Optional<StudentGroupAssignment> current) {
    var conflicting =
        assignmentRepository.findRunningOnOrAfter(studentId, startDate).stream()
            .filter(
                assignment ->
                    current.isEmpty() || !assignment.getId().equals(current.get().getId()))
            .toList();
    if (!conflicting.isEmpty()) {
      throw new ConflictException(
          "Another group assignment already covers a period starting on " + startDate);
    }
  }
}
