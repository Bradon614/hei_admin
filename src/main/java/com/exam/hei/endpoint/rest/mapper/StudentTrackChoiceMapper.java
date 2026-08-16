package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.StudentTrackChoice;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StudentTrackChoiceMapper {
  private final TrackMapper trackMapper;
  private final SemesterMapper semesterMapper;

  public StudentTrackChoice toRest(com.exam.hei.repository.model.StudentTrackChoice domain) {
    return StudentTrackChoice.builder()
        .id(domain.getId())
        .studentId(domain.getStudent() == null ? null : domain.getStudent().getId())
        .track(trackMapper.toRest(domain.getTrack()))
        .fromSemester(semesterMapper.toRest(domain.getFromSemester()))
        .decidedAt(domain.getDecidedAt())
        .reason(domain.getReason())
        .build();
  }
}
