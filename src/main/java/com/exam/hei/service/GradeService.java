package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.endpoint.rest.security.GradeAuthorizer;
import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.GradeChange;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.GradeHistoryRepository;
import com.exam.hei.repository.GradeRepository;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.GradeChangeReasonType;
import com.exam.hei.repository.model.GradeHistory;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry and consultation of grades, with an immutable history of every change.
 *
 * <p>A grade is attached to a student and an exam, never to a group: this is what lets a grade
 * survive a group change. Every write here also appends one {@link GradeHistory} row; {@code
 * Grade.value} is only ever the current state, the trail lives entirely in that other table.
 */
@Service
@AllArgsConstructor
public class GradeService {

  private final GradeRepository gradeRepository;
  private final GradeHistoryRepository gradeHistoryRepository;
  private final StudentRepository studentRepository;
  private final StudentGroupAssignmentRepository studentGroupAssignmentRepository;
  private final TeachingAssignmentRepository teachingAssignmentRepository;
  private final ExamService examService;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentAuthorizer studentAuthorizer;
  private final TeacherAuthorizer teacherAuthorizer;
  private final GradeAuthorizer gradeAuthorizer;
  private final AuthenticatedResourceProvider authenticatedResourceProvider;

  @Transactional
  public List<Grade> crupdate(UUID examId, List<GradeChange> changes) {
    var exam = examService.findById(examId);
    teacherAuthorizer.checkCanEditExamsOf(exam.getCourse().getId());
    return changes.stream().map(change -> apply(exam, change)).toList();
  }

  private Grade apply(Exam exam, GradeChange change) {
    var student = requireStudent(change.studentId());
    checkTrackCoversTheCourse(student.getId(), exam);
    checkValueInRange(change.value());
    checkReasonProvided(change.reasonType(), change.reason());

    var existing = gradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId());
    checkReasonTypeMatchesEntryState(existing, change.reasonType());
    var oldValue = existing.map(Grade::getValue).orElse(null);
    checkValueActuallyChanges(oldValue, change.value());

    var now = Instant.now();
    var grade = existing.orElseGet(() -> Grade.builder().student(student).exam(exam).build());
    grade.setValue(change.value());
    grade.setUpdatedAt(now);
    var saved = gradeRepository.save(grade);

    gradeHistoryRepository.save(
        GradeHistory.builder()
            .grade(saved)
            .oldValue(oldValue)
            .newValue(change.value())
            .reasonType(change.reasonType())
            .reason(change.reason())
            .changedBy(authenticatedResourceProvider.getAuthenticatedUser())
            .build());

    return saved;
  }

  public List<Grade> findAllByExamId(UUID examId) {
    var exam = examService.findById(examId);
    gradeAuthorizer.checkCanReadExamGrades(exam.getCourse().getId());
    var grades = gradeRepository.findAllByExamId(examId);

    if (authenticatedResourceProvider.getAuthenticatedUser().getRole() != Role.TEACHER) {
      return grades;
    }
    return grades.stream().filter(grade -> inACoveredGroupAt(grade, exam)).toList();
  }

  public List<Grade> findAllByStudentId(UUID studentId, SemesterRef semesterRef) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return semesterRef == null
        ? gradeRepository.findAllByStudentId(studentId)
        : gradeRepository.findAllByStudentIdAndSemesterRef(studentId, semesterRef);
  }

  public Grade findById(UUID id) {
    var grade =
        gradeRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Grade " + id + " not found"));
    gradeAuthorizer.checkCanRead(grade.getStudent().getId(), grade.getExam().getCourse().getId());
    return grade;
  }

  public List<GradeHistory> findHistoryOf(UUID gradeId) {
    var grade = findById(gradeId);
    return gradeHistoryRepository.findAllByGradeIdOrderByChangedAtDesc(grade.getId());
  }

  private Student requireStudent(UUID studentId) {
    return studentRepository
        .findById(studentId)
        .orElseThrow(() -> new NotFoundException("Student " + studentId + " not found"));
  }

  /**
   * A course belongs to a student's curriculum only once their track choice covers its semester,
   * from S4 on. The specification narrows the 409 to exactly this case: a grade on a course the
   * student's chosen track does not follow is not additionally blocked here.
   */
  private void checkTrackCoversTheCourse(UUID studentId, Exam exam) {
    var resolution = trackChoiceService.resolve(studentId, exam.getCourse().getSemester());
    if (resolution.isNotSelected()) {
      throw new ConflictException(
          "TRACK_NOT_SELECTED: course "
              + exam.getCourse().getRef()
              + " belongs to a semester the student has not chosen a track for");
    }
  }

  private void checkValueInRange(BigDecimal value) {
    if (value == null
        || value.compareTo(BigDecimal.ZERO) < 0
        || value.compareTo(new BigDecimal("20")) > 0) {
      throw new BadRequestException("A grade must be between 0 and 20");
    }
  }

  private void checkReasonProvided(GradeChangeReasonType reasonType, String reason) {
    if (reasonType == null) {
      throw new BadRequestException("A grade change must state its reason_type");
    }
    if (reason == null || reason.isBlank()) {
      throw new BadRequestException("A grade change must state its reason");
    }
  }

  /** CREATION is reserved for the first entry: mandatory then, forbidden on every later change. */
  private void checkReasonTypeMatchesEntryState(
      Optional<Grade> existing, GradeChangeReasonType reasonType) {
    if (existing.isEmpty() && reasonType != GradeChangeReasonType.CREATION) {
      throw new BadRequestException("The first grade entry must use reason_type CREATION");
    }
    if (existing.isPresent() && reasonType == GradeChangeReasonType.CREATION) {
      throw new BadRequestException("reason_type CREATION is reserved for the first entry");
    }
  }

  /**
   * The database forbids a history row where nothing changed (CHECK old_value IS DISTINCT FROM
   * new_value): rejected here first so it surfaces as a clean 400 rather than a raw constraint
   * violation.
   */
  private void checkValueActuallyChanges(BigDecimal oldValue, BigDecimal newValue) {
    if (oldValue != null && oldValue.compareTo(newValue) == 0) {
      throw new BadRequestException("The new value equals the current value");
    }
  }

  /**
   * Which group the student belonged to on the exam date, compared to the groups a teaching
   * assignment names this teacher for on that course.
   */
  private boolean inACoveredGroupAt(Grade grade, Exam exam) {
    var coveredGroupIds =
        teachingAssignmentRepository
            .findAllByCourseIdAndTeacherId(
                exam.getCourse().getId(),
                authenticatedResourceProvider.getAuthenticatedTeacherId().orElseThrow())
            .stream()
            .map(assignment -> assignment.getGroup().getId())
            .collect(Collectors.toSet());
    var examDate = LocalDate.ofInstant(exam.getDateExam(), ZoneOffset.UTC);

    return studentGroupAssignmentRepository
        .findActiveAt(grade.getStudent().getId(), examDate)
        .map(assignment -> coveredGroupIds.contains(assignment.getGroup().getId()))
        .orElse(false);
  }
}
