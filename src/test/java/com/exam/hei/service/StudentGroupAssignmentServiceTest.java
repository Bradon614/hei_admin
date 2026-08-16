package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.StudentGroupAssignmentRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.model.Group;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.StudentGroupAssignment;
import com.exam.hei.repository.model.Track;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentGroupAssignmentServiceTest {
  private final StudentGroupAssignmentRepository assignmentRepository =
      mock(StudentGroupAssignmentRepository.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final GroupService groupService = mock(GroupService.class);
  private final StudentTrackChoiceService trackChoiceService =
      mock(StudentTrackChoiceService.class);
  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final StudentGroupAssignmentService subject =
      new StudentGroupAssignmentService(
          assignmentRepository,
          studentRepository,
          groupService,
          trackChoiceService,
          studentAuthorizer);

  private static final UUID STUDENT_ID = UUID.randomUUID();
  private static final UUID PROMOTION_ID = UUID.randomUUID();

  private static final Track EL =
      Track.builder().id(UUID.randomUUID()).code("EL").name("Software Ecosystem").build();
  private static final Track TN =
      Track.builder().id(UUID.randomUUID()).code("TN").name("Digital Transformation").build();

  private static final LocalDate FIRST_OF_SEPTEMBER = LocalDate.of(2025, 9, 1);
  private static final LocalDate FIFTEENTH_OF_NOVEMBER = LocalDate.of(2025, 11, 15);

  private Group group(String ref, Track track) {
    var group =
        Group.builder()
            .id(UUID.randomUUID())
            .ref(ref)
            .promotion(Promotion.builder().id(PROMOTION_ID).ref("K").build())
            .track(track)
            .build();
    when(groupService.findById(group.getId())).thenReturn(group);
    return group;
  }

  private void studentExists() {
    when(studentRepository.findById(STUDENT_ID))
        .thenReturn(
            Optional.of(
                Student.builder()
                    .id(STUDENT_ID)
                    .promotion(Promotion.builder().id(PROMOTION_ID).ref("K").build())
                    .build()));
  }

  private void noAssignmentYet() {
    when(assignmentRepository.findByStudentIdAndEndDateIsNull(STUDENT_ID))
        .thenReturn(Optional.empty());
    when(assignmentRepository.findRunningOnOrAfter(any(), any())).thenReturn(List.of());
  }

  private StudentGroupAssignment currentlyIn(Group group, LocalDate since) {
    var open =
        StudentGroupAssignment.builder()
            .id(UUID.randomUUID())
            .group(group)
            .startDate(since)
            .build();
    when(assignmentRepository.findByStudentIdAndEndDateIsNull(STUDENT_ID))
        .thenReturn(Optional.of(open));
    when(assignmentRepository.findRunningOnOrAfter(any(), any())).thenReturn(List.of(open));
    return open;
  }

  private void repositoryEchoesWhatItIsGiven() {
    when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private void studentFollows(Track track) {
    when(trackChoiceService.exitTrackOf(STUDENT_ID)).thenReturn(Optional.ofNullable(track));
  }

  @Test
  void a_first_assignment_opens_without_closing_anything() {
    studentExists();
    studentFollows(null);
    noAssignmentYet();
    repositoryEchoesWhatItIsGiven();
    var k1 = group("K1", null);

    var saved = subject.changeGroup(STUDENT_ID, k1.getId(), FIRST_OF_SEPTEMBER, "entry");

    assertEquals(k1, saved.getGroup());
    assertEquals(FIRST_OF_SEPTEMBER, saved.getStartDate());
    assertNull(saved.getEndDate());
  }

  @Test
  void a_change_closes_the_previous_assignment_on_the_day_before() {
    studentExists();
    studentFollows(null);
    repositoryEchoesWhatItIsGiven();
    var k1 = group("K1", null);
    var k2 = group("K2", null);
    var open = currentlyIn(k1, FIRST_OF_SEPTEMBER);

    var saved = subject.changeGroup(STUDENT_ID, k2.getId(), FIFTEENTH_OF_NOVEMBER, null);

    assertEquals(LocalDate.of(2025, 11, 14), open.getEndDate());
    assertEquals(FIFTEENTH_OF_NOVEMBER, saved.getStartDate());
    assertNull(saved.getEndDate());
  }

  @Test
  void a_change_cannot_start_before_the_assignment_it_closes() {
    studentExists();
    studentFollows(null);
    var k1 = group("K1", null);
    var k2 = group("K2", null);
    currentlyIn(k1, FIFTEENTH_OF_NOVEMBER);

    assertThrows(
        BadRequestException.class,
        () -> subject.changeGroup(STUDENT_ID, k2.getId(), FIRST_OF_SEPTEMBER, null));
  }

  @Test
  void another_assignment_covering_the_period_is_a_conflict() {
    studentExists();
    studentFollows(null);
    var k2 = group("K2", null);
    when(assignmentRepository.findByStudentIdAndEndDateIsNull(STUDENT_ID))
        .thenReturn(Optional.empty());
    when(assignmentRepository.findRunningOnOrAfter(any(), any()))
        .thenReturn(List.of(StudentGroupAssignment.builder().id(UUID.randomUUID()).build()));

    assertThrows(
        ConflictException.class,
        () -> subject.changeGroup(STUDENT_ID, k2.getId(), FIFTEENTH_OF_NOVEMBER, null));
  }

  @Test
  void a_group_of_another_promotion_is_refused() {
    studentExists();
    var foreign =
        Group.builder()
            .id(UUID.randomUUID())
            .ref("H1")
            .promotion(Promotion.builder().id(UUID.randomUUID()).ref("H").build())
            .build();
    when(groupService.findById(foreign.getId())).thenReturn(foreign);

    assertThrows(
        BadRequestException.class,
        () -> subject.changeGroup(STUDENT_ID, foreign.getId(), FIRST_OF_SEPTEMBER, null));
  }

  @Test
  void a_student_without_a_track_cannot_enter_a_track_group() {
    studentExists();
    studentFollows(null);
    noAssignmentYet();
    var k3el = group("K3-EL", EL);

    var thrown =
        assertThrows(
            ConflictException.class,
            () -> subject.changeGroup(STUDENT_ID, k3el.getId(), FIRST_OF_SEPTEMBER, null));

    org.junit.jupiter.api.Assertions.assertTrue(
        thrown.getMessage().contains("TRACK_NOT_SELECTED"), thrown.getMessage());
  }

  @Test
  void a_student_without_a_track_enters_a_common_core_group() {
    studentExists();
    studentFollows(null);
    noAssignmentYet();
    repositoryEchoesWhatItIsGiven();
    var k1 = group("K1", null);

    assertEquals(
        k1, subject.changeGroup(STUDENT_ID, k1.getId(), FIRST_OF_SEPTEMBER, null).getGroup());
  }

  @Test
  void an_el_student_joins_an_el_group() {
    studentExists();
    studentFollows(EL);
    noAssignmentYet();
    repositoryEchoesWhatItIsGiven();
    var k3el = group("K3-EL", EL);

    assertEquals(
        k3el,
        subject.changeGroup(STUDENT_ID, k3el.getId(), FIFTEENTH_OF_NOVEMBER, null).getGroup());
  }

  @Test
  void an_el_student_moves_freely_between_el_groups() {
    studentExists();
    studentFollows(EL);
    repositoryEchoesWhatItIsGiven();
    var k3el = group("K3-EL", EL);
    var k4el = group("K4-EL", EL);
    currentlyIn(k3el, FIRST_OF_SEPTEMBER);

    assertEquals(
        k4el,
        subject.changeGroup(STUDENT_ID, k4el.getId(), FIFTEENTH_OF_NOVEMBER, null).getGroup());
  }

  @Test
  void an_el_student_cannot_join_a_tn_group() {
    studentExists();
    studentFollows(EL);
    noAssignmentYet();
    var k5tn = group("K5-TN", TN);

    assertThrows(
        ConflictException.class,
        () -> subject.changeGroup(STUDENT_ID, k5tn.getId(), FIFTEENTH_OF_NOVEMBER, null));
  }

  @Test
  void a_student_who_chose_cannot_go_back_to_a_common_core_group() {
    studentExists();
    studentFollows(EL);
    noAssignmentYet();
    var k1 = group("K1", null);

    assertThrows(
        ConflictException.class,
        () -> subject.changeGroup(STUDENT_ID, k1.getId(), FIFTEENTH_OF_NOVEMBER, null));
  }

  @Test
  void a_refused_change_writes_nothing() {
    studentExists();
    studentFollows(EL);
    noAssignmentYet();
    var k5tn = group("K5-TN", TN);

    assertThrows(
        ConflictException.class,
        () -> subject.changeGroup(STUDENT_ID, k5tn.getId(), FIFTEENTH_OF_NOVEMBER, null));
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  void reading_the_history_of_a_student_is_authorized_first() {
    studentExists();
    when(assignmentRepository.findAllByStudentIdOrderByStartDateAsc(STUDENT_ID))
        .thenReturn(List.of());

    subject.findAllByStudentId(STUDENT_ID);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void reading_the_group_at_a_date_is_authorized_first() {
    studentExists();
    when(assignmentRepository.findActiveAt(STUDENT_ID, FIRST_OF_SEPTEMBER))
        .thenReturn(Optional.empty());

    subject.findActiveAt(STUDENT_ID, FIRST_OF_SEPTEMBER);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void the_history_of_an_unknown_student_is_not_found() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findAllByStudentId(STUDENT_ID));
  }

  @Test
  void the_current_group_is_the_one_of_the_open_assignment() {
    var k2 = Group.builder().id(UUID.randomUUID()).ref("K2").build();
    when(assignmentRepository.findByStudentIdAndEndDateIsNull(STUDENT_ID))
        .thenReturn(Optional.of(StudentGroupAssignment.builder().group(k2).build()));

    assertEquals(Optional.of(k2), subject.currentGroupOf(STUDENT_ID));
  }
}
