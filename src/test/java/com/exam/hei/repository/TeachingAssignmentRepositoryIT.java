package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Teacher;
import com.exam.hei.repository.model.TeachingAssignment;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TeachingAssignmentRepositoryIT extends FacadeIT {

  @Autowired TeachingAssignmentRepository teachingAssignmentRepository;
  @Autowired CourseRepository courseRepository;
  @Autowired TeacherRepository teacherRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Course course() {
    return courseRepository.save(
        Course.builder()
            .ref(rand(20))
            .title("Course under test")
            .credits(6)
            .semester(semesterRepository.findByRef(SemesterRef.S1).orElseThrow())
            .build());
  }

  private Teacher teacher() {
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.TEACHER)
                .build());
    return teacherRepository.save(
        Teacher.builder()
            .ref(rand(20))
            .firstName("Aina")
            .lastName("Randria")
            .email(rand(12) + "@hei.test")
            .user(account)
            .build());
  }

  private Group group() {
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    return groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion).build());
  }

  private TeachingAssignment.TeachingAssignmentBuilder assignment(
      Course course, Teacher teacher, Group group) {
    return TeachingAssignment.builder().course(course).teacher(teacher).group(group);
  }

  @Test
  void an_assignment_binds_a_course_a_teacher_and_a_group() {
    var course = course();
    var teacher = teacher();
    var group = group();

    var saved = teachingAssignmentRepository.save(assignment(course, teacher, group).build());

    var found = teachingAssignmentRepository.findById(saved.getId()).orElseThrow();
    assertEquals(course.getId(), found.getCourse().getId());
    assertEquals(teacher.getId(), found.getTeacher().getId());
    assertEquals(group.getId(), found.getGroup().getId());
  }

  @Test
  void the_same_triple_cannot_be_assigned_twice() {
    var course = course();
    var teacher = teacher();
    var group = group();
    teachingAssignmentRepository.saveAndFlush(assignment(course, teacher, group).build());

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            teachingAssignmentRepository.saveAndFlush(assignment(course, teacher, group).build()));
  }

  @Test
  void a_teacher_may_be_assigned_the_same_course_for_two_different_groups() {
    // Two rows, same (course, teacher), different group: the same teacher gives the same course to
    // two groups. Not a duplicate, since the group is part of the key.
    var course = course();
    var teacher = teacher();

    var first =
        teachingAssignmentRepository.saveAndFlush(assignment(course, teacher, group()).build());
    var second =
        teachingAssignmentRepository.saveAndFlush(assignment(course, teacher, group()).build());

    assertFalse(first.getId().equals(second.getId()));
  }

  @Test
  void assignments_are_found_by_teacher() {
    var teacher = teacher();
    var mine = teachingAssignmentRepository.save(assignment(course(), teacher, group()).build());
    teachingAssignmentRepository.save(assignment(course(), teacher(), group()).build());

    var found = teachingAssignmentRepository.findAllByTeacherId(teacher.getId());

    assertEquals(1, found.size());
    assertEquals(mine.getId(), found.get(0).getId());
  }

  @Test
  void existence_is_checked_by_course_and_teacher_regardless_of_group() {
    var course = course();
    var teacher = teacher();
    teachingAssignmentRepository.save(assignment(course, teacher, group()).build());

    assertTrue(
        teachingAssignmentRepository.existsByCourseIdAndTeacherId(course.getId(), teacher.getId()));
    assertFalse(
        teachingAssignmentRepository.existsByCourseIdAndTeacherId(
            course.getId(), teacher().getId()));
  }
}
