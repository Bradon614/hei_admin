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

@Service
@AllArgsConstructor
public class StudentGroupAssignmentService {
  private final StudentGroupAssignmentRepository assignmentRepository;
  private final StudentRepository studentRepository;
  private final GroupService groupService;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentAuthorizer studentAuthorizer;

  public List<StudentGroupAssignment> findAllByStudentId(UUID studentId) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return assignmentRepository.findAllByStudentIdOrderByStartDateAsc(studentId);
  }

  public Optional<StudentGroupAssignment> findActiveAt(UUID studentId, LocalDate date) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return assignmentRepository.findActiveAt(studentId, date);
  }

  public Optional<Group> currentGroupOf(UUID studentId) {
    return assignmentRepository
        .findByStudentIdAndEndDateIsNull(studentId)
        .map(StudentGroupAssignment::getGroup);
  }

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
