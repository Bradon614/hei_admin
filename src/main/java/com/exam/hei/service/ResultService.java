package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.CourseResult;
import com.exam.hei.model.GraduationBlocker;
import com.exam.hei.model.GraduationBlockerCode;
import com.exam.hei.model.Pagination;
import com.exam.hei.model.SemesterResult;
import com.exam.hei.model.SemesterResultStatus;
import com.exam.hei.model.StudentResult;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.GradeRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Exam;
import com.exam.hei.repository.model.Grade;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.Student;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ResultService {
  private static final int SCALE = 2;

  private final StudentRepository studentRepository;
  private final SemesterRepository semesterRepository;
  private final GradeRepository gradeRepository;
  private final ExamService examService;
  private final StudentCourseService studentCourseService;
  private final StudentTrackChoiceService trackChoiceService;
  private final PromotionService promotionService;
  private final StudentAuthorizer studentAuthorizer;

  public StudentResult resultOf(UUID studentId) {
    studentAuthorizer.checkCanRead(studentId);
    var student = requireStudent(studentId);
    return resultOf(student);
  }

  public List<StudentResult> resultsOfPromotion(
      UUID promotionId, String trackCode, int page, int pageSize) {
    promotionService.findById(promotionId);
    var pageRequest = Pagination.toPageRequest(page, pageSize, Sort.by("ref"));
    var students =
        trackCode == null
            ? studentRepository.findAllByPromotionId(promotionId, pageRequest)
            : studentRepository.findAllByPromotionIdFollowingTrack(
                promotionId, trackCode, pageRequest);
    return students.stream().map(this::resultOf).toList();
  }

  private StudentResult resultOf(Student student) {
    var gradesByExamId =
        gradeRepository.findAllByStudentId(student.getId()).stream()
            .collect(Collectors.toMap(g -> g.getExam().getId(), Function.identity()));

    var semesterResults =
        semesterRepository.findAllByOrderBySemOrderAsc().stream()
            .map(semester -> semesterResultOf(student.getId(), semester, gradesByExamId))
            .toList();

    var totalCredits = semesterResults.stream().mapToInt(SemesterResult::obtainedCredits).sum();
    var generalAverage = generalAverageOf(semesterResults);
    var graduated = semesterResults.stream().allMatch(SemesterResult::validated);
    var blockers =
        semesterResults.stream().filter(sr -> !sr.validated()).map(this::blockerOf).toList();

    return new StudentResult(
        student, semesterResults, totalCredits, generalAverage, graduated, blockers);
  }

  private SemesterResult semesterResultOf(
      UUID studentId, Semester semester, Map<UUID, Grade> gradesByExamId) {
    var resolution = trackChoiceService.resolve(studentId, semester);
    if (resolution.isNotSelected()) {
      return new SemesterResult(
          semester,
          SemesterResultStatus.TRACK_NOT_SELECTED,
          null,
          0,
          semester.getRequiredCredits(),
          false,
          List.of());
    }

    var courseResults =
        studentCourseService.findApplicable(studentId, semester.getRef()).stream()
            .map(course -> courseResultOf(course, gradesByExamId))
            .toList();
    var obtainedCredits = courseResults.stream().mapToInt(CourseResult::obtainedCredits).sum();

    return new SemesterResult(
        semester,
        SemesterResultStatus.EVALUATED,
        resolution.isResolved() ? resolution.track() : null,
        obtainedCredits,
        semester.getRequiredCredits(),
        obtainedCredits >= semester.getRequiredCredits(),
        courseResults);
  }

  private CourseResult courseResultOf(Course course, Map<UUID, Grade> gradesByExamId) {
    List<Exam> exams = examService.findAllByCourseId(course.getId());
    if (exams.isEmpty()) {
      return new CourseResult(course, null, false, 0);
    }

    var numerator = BigDecimal.ZERO;
    var denominator = BigDecimal.ZERO;
    for (var exam : exams) {
      var grade = gradesByExamId.get(exam.getId());
      var value = grade == null ? BigDecimal.ZERO : grade.getValue();
      numerator = numerator.add(value.multiply(exam.getCoefficient()));
      denominator = denominator.add(exam.getCoefficient());
    }

    var finalGrade = numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    var validated = finalGrade.compareTo(BigDecimal.TEN) >= 0;
    return new CourseResult(course, finalGrade, validated, validated ? course.getCredits() : 0);
  }

  private BigDecimal generalAverageOf(List<SemesterResult> semesterResults) {
    var numerator = BigDecimal.ZERO;
    var denominator = BigDecimal.ZERO;
    for (var semesterResult : semesterResults) {
      for (var courseResult : semesterResult.courseResults()) {
        if (courseResult.finalGrade() == null) {
          continue;
        }
        var credits = BigDecimal.valueOf(courseResult.course().getCredits());
        numerator = numerator.add(courseResult.finalGrade().multiply(credits));
        denominator = denominator.add(credits);
      }
    }
    return denominator.compareTo(BigDecimal.ZERO) == 0
        ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
        : numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
  }

  private GraduationBlocker blockerOf(SemesterResult semesterResult) {
    if (semesterResult.status() == SemesterResultStatus.TRACK_NOT_SELECTED) {
      return new GraduationBlocker(
          GraduationBlockerCode.TRACK_NOT_SELECTED,
          semesterResult.semester().getRef(),
          "No track selected for " + semesterResult.semester().getRef());
    }
    return new GraduationBlocker(
        GraduationBlockerCode.SEMESTER_NOT_VALIDATED,
        semesterResult.semester().getRef(),
        semesterResult.semester().getRef()
            + " not validated: "
            + semesterResult.obtainedCredits()
            + "/"
            + semesterResult.requiredCredits()
            + " credits");
  }

  private Student requireStudent(UUID studentId) {
    return studentRepository
        .findById(studentId)
        .orElseThrow(() -> new NotFoundException("Student " + studentId + " not found"));
  }
}
