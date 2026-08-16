package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class StudentCourseService {
  private final CourseRepository courseRepository;
  private final SemesterRepository semesterRepository;
  private final StudentRepository studentRepository;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentAuthorizer studentAuthorizer;

  public List<Course> findApplicable(UUID studentId, SemesterRef semesterRef) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);

    var semesters =
        semesterRef == null
            ? semesterRepository.findAllByOrderBySemOrderAsc()
            : List.of(requireSemester(semesterRef));

    return semesters.stream()
        .flatMap(semester -> applicableAt(studentId, semester).stream())
        .toList();
  }

  private List<Course> applicableAt(UUID studentId, Semester semester) {
    var resolution = trackChoiceService.resolve(studentId, semester);
    return switch (resolution.status()) {
      case COMMON_CORE -> courseRepository.findAllBySemesterIdOrderByRefAsc(semester.getId());
      case RESOLVED ->
          courseRepository.findAllOfSemesterFollowedByTrack(
              semester.getId(), resolution.track().getId());
      case TRACK_NOT_SELECTED -> List.of();
    };
  }

  private void requireStudent(UUID studentId) {
    if (!studentRepository.existsById(studentId)) {
      throw new NotFoundException("Student " + studentId + " not found");
    }
  }

  private Semester requireSemester(SemesterRef ref) {
    return semesterRepository
        .findByRef(ref)
        .orElseThrow(() -> new NotFoundException("Semester " + ref + " not found"));
  }
}
