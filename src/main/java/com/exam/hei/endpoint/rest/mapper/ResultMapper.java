package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.CourseResult;
import com.exam.hei.endpoint.rest.model.GraduationBlocker;
import com.exam.hei.endpoint.rest.model.SemesterResult;
import com.exam.hei.endpoint.rest.model.Student;
import com.exam.hei.endpoint.rest.model.StudentResult;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pure conversion from the computed domain results to their REST representation.
 *
 * <p>One way only: nothing behind these types is ever written. The REST {@link Student} is passed
 * in rather than built here, since resolving its computed {@code current_track} and {@code
 * current_group} needs services this mapper has no business calling.
 */
@Component
@AllArgsConstructor
public class ResultMapper {

  private final CourseMapper courseMapper;
  private final SemesterMapper semesterMapper;
  private final TrackMapper trackMapper;

  public StudentResult toRest(com.exam.hei.model.StudentResult domain, Student restStudent) {
    return StudentResult.builder()
        .student(restStudent)
        .semesterResults(domain.semesterResults().stream().map(this::toRest).toList())
        .totalObtainedCredits(domain.totalObtainedCredits())
        .generalAverage(domain.generalAverage())
        .graduated(domain.graduated())
        .blockers(domain.blockers().stream().map(this::toRest).toList())
        .build();
  }

  private SemesterResult toRest(com.exam.hei.model.SemesterResult domain) {
    return SemesterResult.builder()
        .semester(semesterMapper.toRest(domain.semester()))
        .status(domain.status())
        .track(domain.track() == null ? null : trackMapper.toRest(domain.track()))
        .obtainedCredits(domain.obtainedCredits())
        .requiredCredits(domain.requiredCredits())
        .validated(domain.validated())
        .courseResults(domain.courseResults().stream().map(this::toRest).toList())
        .build();
  }

  private CourseResult toRest(com.exam.hei.model.CourseResult domain) {
    return CourseResult.builder()
        .course(courseMapper.toRest(domain.course()))
        .finalGrade(domain.finalGrade())
        .validated(domain.validated())
        .obtainedCredits(domain.obtainedCredits())
        .build();
  }

  private GraduationBlocker toRest(com.exam.hei.model.GraduationBlocker domain) {
    return GraduationBlocker.builder()
        .code(domain.code())
        .semesterRef(domain.semesterRef())
        .message(domain.message())
        .build();
  }
}
