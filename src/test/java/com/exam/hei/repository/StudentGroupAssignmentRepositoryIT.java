package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentGroupAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class StudentGroupAssignmentRepositoryIT extends FacadeIT {
  @Autowired StudentGroupAssignmentRepository assignmentRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired GroupRepository groupRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired AppUserRepository appUserRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Promotion promotion;

  private Promotion promotion() {
    if (promotion == null) {
      promotion =
          promotionRepository.save(
              Promotion.builder()
                  .ref(TestRefs.promotionRef())
                  .name("Promotion under test")
                  .startYear(2025)
                  .endYear(2028)
                  .build());
    }
    return promotion;
  }

  private Group group() {
    return groupRepository.save(Group.builder().ref(rand(10)).promotion(promotion()).build());
  }

  private Student student() {
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion())
            .user(account)
            .build());
  }

  private StudentGroupAssignment assign(Student student, Group group, String start, String end) {
    return assignmentRepository.saveAndFlush(
        StudentGroupAssignment.builder()
            .student(student)
            .group(group)
            .startDate(LocalDate.parse(start))
            .endDate(end == null ? null : LocalDate.parse(end))
            .build());
  }

  @Test
  void a_student_can_go_through_four_groups_at_arbitrary_moments() {
    var jean = student();
    var k1 = group();
    var k2 = group();
    var k3 = group();

    assign(jean, k1, "2025-09-01", "2025-11-14");
    assign(jean, k2, "2025-11-15", "2026-02-09");
    assign(jean, k3, "2026-02-10", "2026-04-19");
    assign(jean, k1, "2026-04-20", null);

    var history = assignmentRepository.findAllByStudentIdOrderByStartDateAsc(jean.getId());

    assertEquals(4, history.size());
    assertEquals(
        List.of(k1.getId(), k2.getId(), k3.getId(), k1.getId()),
        history.stream().map(a -> a.getGroup().getId()).toList());
  }

  @Test
  void the_group_of_a_student_on_a_given_date_is_resolvable() {
    var jean = student();
    var k1 = group();
    var k2 = group();
    assign(jean, k1, "2025-09-01", "2025-11-14");
    assign(jean, k2, "2025-11-15", null);

    assertEquals(
        k1.getId(),
        assignmentRepository
            .findActiveAt(jean.getId(), LocalDate.parse("2025-10-01"))
            .orElseThrow()
            .getGroup()
            .getId());
    assertEquals(
        k2.getId(),
        assignmentRepository
            .findActiveAt(jean.getId(), LocalDate.parse("2026-05-01"))
            .orElseThrow()
            .getGroup()
            .getId());
  }

  @Test
  void the_bounds_of_a_period_are_inclusive() {
    var jean = student();
    var k1 = group();
    assign(jean, k1, "2025-09-01", "2025-11-14");

    assertTrue(
        assignmentRepository.findActiveAt(jean.getId(), LocalDate.parse("2025-09-01")).isPresent());
    assertTrue(
        assignmentRepository.findActiveAt(jean.getId(), LocalDate.parse("2025-11-14")).isPresent());
    assertTrue(
        assignmentRepository.findActiveAt(jean.getId(), LocalDate.parse("2025-11-15")).isEmpty());
  }

  @Test
  void a_student_who_never_moved_has_a_single_assignment() {
    var jean = student();
    assign(jean, group(), "2025-09-01", null);

    assertEquals(
        1, assignmentRepository.findAllByStudentIdOrderByStartDateAsc(jean.getId()).size());
  }

  @Test
  void the_open_assignment_is_the_current_one() {
    var jean = student();
    var k2 = group();
    assign(jean, group(), "2025-09-01", "2025-11-14");
    assign(jean, k2, "2025-11-15", null);

    assertEquals(
        k2.getId(),
        assignmentRepository
            .findByStudentIdAndEndDateIsNull(jean.getId())
            .orElseThrow()
            .getGroup()
            .getId());
  }

  @Test
  void consecutive_periods_do_not_overlap() {
    var jean = student();
    assign(jean, group(), "2025-09-01", "2025-11-14");

    assertDoesNotThrow(() -> assign(jean, group(), "2025-11-15", null));
  }

  @Test
  void overlapping_periods_are_refused_by_the_database() {
    var jean = student();
    assign(jean, group(), "2025-09-01", "2025-11-30");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> assign(jean, group(), "2025-11-15", "2025-12-31"));
  }

  @Test
  void a_conflicting_period_is_detectable_before_writing() {
    var jean = student();
    assign(jean, group(), "2025-09-01", "2025-11-30");

    assertEquals(
        1,
        assignmentRepository
            .findRunningOnOrAfter(jean.getId(), LocalDate.parse("2025-11-15"))
            .size());
  }

  @Test
  void a_period_already_closed_conflicts_with_nothing_after_it() {
    var jean = student();
    assign(jean, group(), "2025-09-01", "2025-11-30");

    assertTrue(
        assignmentRepository
            .findRunningOnOrAfter(jean.getId(), LocalDate.parse("2025-12-01"))
            .isEmpty());
  }

  @Test
  void an_open_ended_period_conflicts_with_everything_after_it() {
    var jean = student();
    assign(jean, group(), "2025-09-01", null);

    assertEquals(
        1,
        assignmentRepository
            .findRunningOnOrAfter(jean.getId(), LocalDate.parse("2026-04-20"))
            .size());
  }

  @Test
  void two_students_may_share_a_group_over_the_same_period() {
    var group = group();
    assign(student(), group, "2025-09-01", null);

    assertDoesNotThrow(() -> assign(student(), group, "2025-09-01", null));
  }
}
