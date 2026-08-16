package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Track;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CourseRepositoryIT extends FacadeIT {
  @Autowired CourseRepository courseRepository;
  @Autowired SemesterRepository semesterRepository;
  @Autowired TrackRepository trackRepository;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Semester semester(SemesterRef ref) {
    return semesterRepository.findByRef(ref).orElseThrow();
  }

  private Track el() {
    return trackRepository.findByCode("EL").orElseThrow();
  }

  private Track tn() {
    return trackRepository.findByCode("TN").orElseThrow();
  }

  private Course course(SemesterRef semesterRef, Track track, int credits) {
    return courseRepository.save(
        Course.builder()
            .ref(rand(20))
            .title("Course under test")
            .credits(credits)
            .semester(semester(semesterRef))
            .track(track)
            .build());
  }

  @Test
  void a_common_course_carries_no_track() {
    var saved = course(SemesterRef.S1, null, 6);

    assertNull(courseRepository.findById(saved.getId()).orElseThrow().getTrack());
  }

  @Test
  void a_specific_course_carries_its_track() {
    var saved = course(SemesterRef.S5, el(), 9);

    assertEquals(
        el().getId(), courseRepository.findById(saved.getId()).orElseThrow().getTrack().getId());
  }

  @Test
  void a_course_belongs_to_exactly_one_semester() {
    var saved = course(SemesterRef.S2, null, 6);

    assertEquals(
        SemesterRef.S2,
        courseRepository.findById(saved.getId()).orElseThrow().getSemester().getRef());
  }

  @Test
  void two_courses_cannot_share_a_ref() {
    var ref = rand(20);
    courseRepository.saveAndFlush(
        Course.builder()
            .ref(ref)
            .title("First")
            .credits(6)
            .semester(semester(SemesterRef.S1))
            .build());

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            courseRepository.saveAndFlush(
                Course.builder()
                    .ref(ref)
                    .title("Second")
                    .credits(6)
                    .semester(semester(SemesterRef.S1))
                    .build()));
  }

  @Test
  void a_course_carries_positive_credits_only() {
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            courseRepository.saveAndFlush(
                Course.builder()
                    .ref(rand(20))
                    .title("Worth nothing")
                    .credits(0)
                    .semester(semester(SemesterRef.S1))
                    .build()));
  }

  @Test
  void a_track_follows_its_own_courses_and_the_common_ones() {
    var common = course(SemesterRef.S5, null, 12);
    var forEl = course(SemesterRef.S5, el(), 18);
    var forTn = course(SemesterRef.S5, tn(), 18);

    var followedByEl =
        courseRepository.findAllOfSemesterFollowedByTrack(
            semester(SemesterRef.S5).getId(), el().getId());

    var ids = followedByEl.stream().map(Course::getId).toList();
    assertTrue(ids.contains(common.getId()), "a common course belongs to every programme");
    assertTrue(ids.contains(forEl.getId()));
    assertFalse(ids.contains(forTn.getId()), "an EL student never follows a TN course");
  }

  @Test
  void the_other_track_sees_the_mirror_image() {
    var common = course(SemesterRef.S6, null, 12);
    var forEl = course(SemesterRef.S6, el(), 18);
    var forTn = course(SemesterRef.S6, tn(), 18);

    var ids =
        courseRepository
            .findAllOfSemesterFollowedByTrack(semester(SemesterRef.S6).getId(), tn().getId())
            .stream()
            .map(Course::getId)
            .toList();

    assertTrue(ids.contains(common.getId()));
    assertTrue(ids.contains(forTn.getId()));
    assertFalse(ids.contains(forEl.getId()));
  }

  @Test
  void a_common_course_is_counted_in_the_credits_of_both_programmes() {
    var s4 = semester(SemesterRef.S4);
    var common = course(SemesterRef.S4, null, 12);
    var forEl = course(SemesterRef.S4, el(), 18);
    var forTn = course(SemesterRef.S4, tn(), 18);

    var thisCurriculum = Set.of(common.getId(), forEl.getId(), forTn.getId());

    var creditsForEl = creditsOf(s4.getId(), el().getId(), thisCurriculum);
    var creditsForTn = creditsOf(s4.getId(), tn().getId(), thisCurriculum);

    assertEquals(30, creditsForEl);
    assertEquals(30, creditsForTn);
    assertEquals(48, common.getCredits() + forEl.getCredits() + forTn.getCredits());
  }

  private int creditsOf(UUID semesterId, UUID trackId, Set<UUID> among) {
    return courseRepository.findAllOfSemesterFollowedByTrack(semesterId, trackId).stream()
        .filter(course -> among.contains(course.getId()))
        .mapToInt(Course::getCredits)
        .sum();
  }

  @Test
  void every_student_follows_all_the_courses_of_a_common_core_semester() {
    var first = course(SemesterRef.S3, null, 15);
    var second = course(SemesterRef.S3, null, 15);

    var ids =
        courseRepository.findAllBySemesterIdOrderByRefAsc(semester(SemesterRef.S3).getId()).stream()
            .map(Course::getId)
            .toList();

    assertTrue(ids.contains(first.getId()));
    assertTrue(ids.contains(second.getId()));
  }
}
