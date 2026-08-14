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

/**
 * Which courses a student actually follows.
 *
 * <p>This is the critical rule of the assignment: an EL student never has a TN-only course in their
 * transcript, and the other way round. It holds because the answer is derived, semester by
 * semester, from the track ruling that semester — never from a property of the student, and never
 * from the group they belong to, which plays no part here at all.
 */
@Service
@AllArgsConstructor
public class StudentCourseService {

  private final CourseRepository courseRepository;
  private final SemesterRepository semesterRepository;
  private final StudentRepository studentRepository;
  private final StudentTrackChoiceService trackChoiceService;
  private final StudentAuthorizer studentAuthorizer;

  /**
   * Read path: a student may only read their own curriculum.
   *
   * @param semesterRef one semester, or every one of them when left out
   */
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

  /**
   * The three cases of the resolution, and nothing else:
   *
   * <ul>
   *   <li>common core: every course of the semester, all of them common by construction;
   *   <li>a track applies: the common courses plus those of that track, so a common course counts
   *       in the programme of both tracks;
   *   <li>no choice covers the semester: no course at all. The reason is carried by the resolution
   *       itself, so that a student who never chose is never mistaken for one who simply failed.
   * </ul>
   */
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
