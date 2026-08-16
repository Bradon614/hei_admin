package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.TeacherAuthorizer;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.TeachingAssignmentRepository;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeachingAssignmentServiceTest {
  private final TeachingAssignmentRepository teachingAssignmentRepository =
      mock(TeachingAssignmentRepository.class);
  private final CourseService courseService = mock(CourseService.class);
  private final TeacherService teacherService = mock(TeacherService.class);
  private final GroupService groupService = mock(GroupService.class);
  private final TeacherAuthorizer teacherAuthorizer = mock(TeacherAuthorizer.class);
  private final TeachingAssignmentService subject =
      new TeachingAssignmentService(
          teachingAssignmentRepository,
          courseService,
          teacherService,
          groupService,
          teacherAuthorizer);

  private static final Course COURSE = Course.builder().id(UUID.randomUUID()).ref("PROG1").build();
  private static final Teacher TEACHER =
      Teacher.builder().id(UUID.randomUUID()).ref("TCH1").build();
  private static final Group GROUP = Group.builder().id(UUID.randomUUID()).ref("K1").build();

  private void referencedResourcesExist() {
    when(courseService.findById(COURSE.getId())).thenReturn(COURSE);
    when(teacherService.findById(TEACHER.getId())).thenReturn(TEACHER);
    when(groupService.findById(GROUP.getId())).thenReturn(GROUP);
  }

  private static TeachingAssignment assignment() {
    return TeachingAssignment.builder()
        .course(Course.builder().id(COURSE.getId()).build())
        .teacher(Teacher.builder().id(TEACHER.getId()).build())
        .group(Group.builder().id(GROUP.getId()).build())
        .build();
  }

  @Test
  void an_assignment_is_created_once_its_three_resources_are_resolved() {
    referencedResourcesExist();
    when(teachingAssignmentRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var saved = subject.saveAll(List.of(assignment())).get(0);

    assertEquals(COURSE, saved.getCourse());
    assertEquals(TEACHER, saved.getTeacher());
    assertEquals(GROUP, saved.getGroup());
  }

  @Test
  void an_assignment_naming_an_unknown_course_is_not_found() {
    when(courseService.findById(COURSE.getId()))
        .thenThrow(new NotFoundException("Course not found"));

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(assignment())));
  }

  @Test
  void an_assignment_naming_an_unknown_teacher_is_not_found() {
    when(courseService.findById(COURSE.getId())).thenReturn(COURSE);
    when(teacherService.findById(TEACHER.getId()))
        .thenThrow(new NotFoundException("Teacher not found"));

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(assignment())));
  }

  @Test
  void an_assignment_naming_an_unknown_group_is_not_found() {
    when(courseService.findById(COURSE.getId())).thenReturn(COURSE);
    when(teacherService.findById(TEACHER.getId())).thenReturn(TEACHER);
    when(groupService.findById(GROUP.getId())).thenThrow(new NotFoundException("Group not found"));

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(assignment())));
  }

  @Test
  void the_same_course_teacher_group_triple_cannot_be_assigned_twice() {
    referencedResourcesExist();
    when(teachingAssignmentRepository.existsByCourseIdAndTeacherIdAndGroupId(
            COURSE.getId(), TEACHER.getId(), GROUP.getId()))
        .thenReturn(true);

    assertThrows(ConflictException.class, () -> subject.saveAll(List.of(assignment())));
  }

  @Test
  void updating_an_existing_assignment_does_not_replay_the_duplicate_check() {
    referencedResourcesExist();
    var id = UUID.randomUUID();
    when(teachingAssignmentRepository.existsById(id)).thenReturn(true);
    when(teachingAssignmentRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    var existing = assignment();
    existing.setId(id);

    subject.saveAll(List.of(existing));

    verify(teachingAssignmentRepository, org.mockito.Mockito.never())
        .existsByCourseIdAndTeacherIdAndGroupId(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updating_an_unknown_assignment_is_not_found() {
    referencedResourcesExist();
    var id = UUID.randomUUID();
    when(teachingAssignmentRepository.existsById(id)).thenReturn(false);
    var unknown = assignment();
    unknown.setId(id);

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(unknown)));
  }

  @Test
  void an_unknown_teacher_s_assignments_are_not_found() {
    when(teacherService.findById(TEACHER.getId()))
        .thenThrow(new NotFoundException("Teacher not found"));

    assertThrows(NotFoundException.class, () -> subject.findAllByTeacherId(TEACHER.getId()));
  }

  @Test
  void reading_a_teacher_s_assignments_is_authorized_first() {
    when(teacherService.findById(TEACHER.getId())).thenReturn(TEACHER);

    subject.findAllByTeacherId(TEACHER.getId());

    verify(teacherAuthorizer).checkCanRead(TEACHER.getId());
  }
}
