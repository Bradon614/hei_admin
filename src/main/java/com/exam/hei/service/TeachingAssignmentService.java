package com.exam.hei.service;

import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.TeachingAssignment;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who teaches which course, to which group.
 *
 * <p>Also the source of truth {@code TeacherAuthorizer} reads to decide whether a teacher may touch
 * the exams of a course: a teacher is in scope for a course the moment one row here names them for
 * it, regardless of the group.
 */
@Service
@AllArgsConstructor
public class TeachingAssignmentService {

  private final TeachingAssignmentRepository teachingAssignmentRepository;
  private final CourseService courseService;
  private final TeacherService teacherService;
  private final GroupService groupService;
  private final TeacherAuthorizer teacherAuthorizer;

  public List<TeachingAssignment> findAllByTeacherId(UUID teacherId) {
    teacherAuthorizer.checkCanRead(teacherId);
    teacherService.findById(teacherId);
    return teachingAssignmentRepository.findAllByTeacherId(teacherId);
  }

  @Transactional
  public List<TeachingAssignment> saveAll(List<TeachingAssignment> assignments) {
    return assignments.stream().map(this::save).toList();
  }

  private TeachingAssignment save(TeachingAssignment assignment) {
    var course = courseService.findById(assignment.getCourse().getId());
    var teacher = teacherService.findById(assignment.getTeacher().getId());
    var group = groupService.findById(assignment.getGroup().getId());

    if (assignment.getId() == null) {
      checkNotAlreadyAssigned(course.getId(), teacher.getId(), group.getId());
    } else {
      requireExisting(assignment.getId());
    }

    assignment.setCourse(course);
    assignment.setTeacher(teacher);
    assignment.setGroup(group);
    return teachingAssignmentRepository.save(assignment);
  }

  private void requireExisting(UUID id) {
    if (!teachingAssignmentRepository.existsById(id)) {
      throw new NotFoundException("Teaching assignment " + id + " not found");
    }
  }

  private void checkNotAlreadyAssigned(UUID courseId, UUID teacherId, UUID groupId) {
    if (teachingAssignmentRepository.existsByCourseIdAndTeacherIdAndGroupId(
        courseId, teacherId, groupId)) {
      throw new ConflictException("This teacher is already assigned to this course for this group");
    }
  }
}
