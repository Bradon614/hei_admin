package com.exam.hei.service;

import com.exam.hei.model.Pagination;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.CourseRepository;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class CourseService {
  private final CourseRepository courseRepository;
  private final SemesterRepository semesterRepository;
  private final TrackService trackService;

  public List<Course> findAll(
      int page,
      int pageSize,
      com.exam.hei.repository.model.SemesterRef semesterRef,
      String trackCode) {
    var pageRequest = Pagination.toPageRequest(page, pageSize, Sort.by("ref"));

    if (semesterRef != null && trackCode != null) {
      return courseRepository
          .findAllOfSemesterFollowedByTrackCode(semesterRef, trackCode, pageRequest)
          .getContent();
    }
    if (semesterRef != null) {
      return courseRepository.findAllBySemesterRef(semesterRef, pageRequest).getContent();
    }
    if (trackCode != null) {
      return courseRepository.findAllFollowedByTrack(trackCode, pageRequest).getContent();
    }
    return courseRepository.findAll(pageRequest).getContent();
  }

  public Course findById(UUID id) {
    return courseRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Course " + id + " not found"));
  }

  @Transactional
  public List<Course> saveAll(List<Course> courses) {
    courses.forEach(this::resolveReferences);
    return courseRepository.saveAll(courses);
  }

  private void resolveReferences(Course course) {
    if (course.getId() != null) {
      findById(course.getId());
    }
    if (course.getCredits() <= 0) {
      throw new BadRequestException("A course is worth at least one credit");
    }
    var semester = requireSemester(course);
    course.setSemester(semester);
    course.setTrack(resolveTrack(course, semester));
  }

  private Semester requireSemester(Course course) {
    if (course.getSemester() == null) {
      throw new NotFoundException("A course must name the semester it belongs to");
    }
    if (course.getSemester().getRef() != null) {
      var ref = course.getSemester().getRef();
      return semesterRepository
          .findByRef(ref)
          .orElseThrow(() -> new NotFoundException("Semester " + ref + " not found"));
    }
    var id = course.getSemester().getId();
    if (id == null) {
      throw new NotFoundException("A course must name the semester it belongs to");
    }
    return semesterRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Semester " + id + " not found"));
  }

  private Track resolveTrack(Course course, Semester semester) {
    if (course.getTrack() == null || course.getTrack().getId() == null) {
      return null;
    }
    if (semester.isCommonCore()) {
      throw new BadRequestException(
          "Semester "
              + semester.getRef()
              + " is common core: its courses are followed by every student and carry no track");
    }
    return trackService.findById(course.getTrack().getId());
  }
}
