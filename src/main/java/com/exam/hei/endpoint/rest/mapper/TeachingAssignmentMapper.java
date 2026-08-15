package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.TeachingAssignment;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Teacher;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pure conversion between the REST and the persistence representations of a teaching assignment.
 *
 * <p>The course, the teacher and the group are carried across as ids only on writes: resolving
 * them, and refusing ones that do not exist, is a business decision left to the service.
 */
@Component
@AllArgsConstructor
public class TeachingAssignmentMapper {

  private final CourseMapper courseMapper;
  private final TeacherMapper teacherMapper;
  private final GroupMapper groupMapper;

  public TeachingAssignment toRest(com.exam.hei.repository.model.TeachingAssignment domain) {
    return TeachingAssignment.builder()
        .id(domain.getId())
        .course(domain.getCourse() == null ? null : courseMapper.toRest(domain.getCourse()))
        .teacher(domain.getTeacher() == null ? null : teacherMapper.toRest(domain.getTeacher()))
        .group(domain.getGroup() == null ? null : groupMapper.toRest(domain.getGroup()))
        .build();
  }

  public com.exam.hei.repository.model.TeachingAssignment toDomain(TeachingAssignment rest) {
    return com.exam.hei.repository.model.TeachingAssignment.builder()
        .id(rest.getId())
        .course(
            rest.getCourse() == null ? null : Course.builder().id(rest.getCourse().getId()).build())
        .teacher(
            rest.getTeacher() == null
                ? null
                : Teacher.builder().id(rest.getTeacher().getId()).build())
        .group(rest.getGroup() == null ? null : Group.builder().id(rest.getGroup().getId()).build())
        .build();
  }
}
