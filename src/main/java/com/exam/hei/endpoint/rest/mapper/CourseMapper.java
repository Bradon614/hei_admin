package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.Track;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The named semester and track are carried across as they were written, by reference or by id.
 * Resolving them, and refusing a track on a common core semester, is a business decision left to
 * the service.
 */
@Component
@AllArgsConstructor
public class CourseMapper {

  private final SemesterMapper semesterMapper;
  private final TrackMapper trackMapper;

  public Course toRest(com.exam.hei.repository.model.Course domain) {
    return Course.builder()
        .id(domain.getId())
        .ref(domain.getRef())
        .title(domain.getTitle())
        .credits(domain.getCredits())
        .semester(domain.getSemester() == null ? null : semesterMapper.toRest(domain.getSemester()))
        .track(domain.getTrack() == null ? null : trackMapper.toRest(domain.getTrack()))
        .build();
  }

  public com.exam.hei.repository.model.Course toDomain(Course rest) {
    return com.exam.hei.repository.model.Course.builder()
        .id(rest.getId())
        .ref(rest.getRef())
        .title(rest.getTitle())
        .credits(rest.getCredits() == null ? 0 : rest.getCredits())
        .semester(semesterOf(rest))
        .track(trackOf(rest))
        .build();
  }

  private Semester semesterOf(Course rest) {
    if (rest.getSemester() == null) {
      return null;
    }
    return Semester.builder()
        .id(rest.getSemester().getId())
        .ref(rest.getSemester().getRef())
        .build();
  }

  private Track trackOf(Course rest) {
    if (rest.getTrack() == null || rest.getTrack().getId() == null) {
      return null;
    }
    return Track.builder().id(rest.getTrack().getId()).build();
  }
}
