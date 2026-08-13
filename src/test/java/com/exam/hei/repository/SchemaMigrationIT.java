package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks that the schema migration really applies and that the constraints carrying a business rule
 * reject what they are meant to reject.
 *
 * <p>Deliberately written against plain SQL rather than JPA entities: the database schema is its
 * own feature, entities belong to the features that need them.
 */
class SchemaMigrationIT extends FacadeIT {

  @Autowired JdbcTemplate jdbcTemplate;

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  // --- fixtures -------------------------------------------------------------

  private UUID insertPromotion(int startYear, int endYear) {
    return jdbcTemplate.queryForObject(
        "insert into promotion (ref, name, start_year, end_year) values (?, ?, ?, ?) returning id",
        UUID.class,
        rand(5),
        "Promotion under test",
        startYear,
        endYear);
  }

  private UUID insertPromotion() {
    return insertPromotion(2025, 2028);
  }

  private UUID insertTrack() {
    return jdbcTemplate.queryForObject(
        "insert into track (code, name) values (?, ?) returning id",
        UUID.class,
        rand(10),
        "Track under test");
  }

  private void openTrack(UUID promotionId, UUID trackId) {
    jdbcTemplate.update(
        "insert into promotion_track (promotion_id, track_id) values (?, ?)", promotionId, trackId);
  }

  private UUID insertGroup(UUID promotionId, UUID trackId) {
    return jdbcTemplate.queryForObject(
        "insert into student_group (ref, promotion_id, track_id) values (?, ?, ?) returning id",
        UUID.class,
        rand(10),
        promotionId,
        trackId);
  }

  private UUID insertUser() {
    return jdbcTemplate.queryForObject(
        "insert into app_user (email, password_hash, role) values (?, ?, ?) returning id",
        UUID.class,
        rand(12) + "@hei.test",
        "hash",
        "STUDENT");
  }

  private UUID insertStudent(UUID promotionId) {
    return jdbcTemplate.queryForObject(
        "insert into student (ref, first_name, last_name, email, entrance_date, promotion_id,"
            + " user_id) values (?, ?, ?, ?, date '2025-09-01', ?, ?) returning id",
        UUID.class,
        rand(20),
        "Jean",
        "Rakoto",
        rand(12) + "@hei.test",
        promotionId,
        insertUser());
  }

  /** Semesters are reference data seeded by a later feature, so insert on demand here. */
  private UUID semester(String ref, int order, int year, boolean commonCore) {
    jdbcTemplate.update(
        "insert into semester (ref, sem_order, year_number, common_core) values (?, ?, ?, ?)"
            + " on conflict (ref) do nothing",
        ref,
        order,
        year,
        commonCore);
    return jdbcTemplate.queryForObject("select id from semester where ref = ?", UUID.class, ref);
  }

  private UUID insertCourse(UUID semesterId) {
    return jdbcTemplate.queryForObject(
        "insert into course (ref, title, credits, semester_id) values (?, ?, ?, ?) returning id",
        UUID.class,
        rand(20),
        "Course under test",
        6,
        semesterId);
  }

  private UUID insertExam(UUID courseId) {
    return jdbcTemplate.queryForObject(
        "insert into exam (course_id, title, date_exam, coefficient) values (?, ?, now(), ?)"
            + " returning id",
        UUID.class,
        courseId,
        rand(10),
        2.0);
  }

  private UUID insertGrade(UUID studentId, UUID examId, double value) {
    return jdbcTemplate.queryForObject(
        "insert into grade (student_id, exam_id, value) values (?, ?, ?) returning id",
        UUID.class,
        studentId,
        examId,
        value);
  }

  private void assignGroup(UUID studentId, UUID groupId, String startDate, String endDate) {
    jdbcTemplate.update(
        "insert into student_group_assignment (student_id, group_id, start_date, end_date)"
            + " values (?, ?, cast(? as date), cast(? as date))",
        studentId,
        groupId,
        startDate,
        endDate);
  }

  // --- migration itself -----------------------------------------------------

  @Test
  void migration_v43_is_applied() {
    var applied =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '43' and success",
            Integer.class);

    assertEquals(1, applied);
  }

  @Test
  void every_domain_table_exists() {
    var expected =
        List.of(
            "app_user",
            "promotion",
            "track",
            "promotion_track",
            "semester",
            "student_group",
            "student",
            "teacher",
            "student_group_assignment",
            "student_track_choice",
            "course",
            "teaching_assignment",
            "exam",
            "grade",
            "grade_history",
            "transcript_request");

    var found =
        jdbcTemplate.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String.class);

    assertTrue(
        found.containsAll(expected),
        "missing tables: " + expected.stream().filter(t -> !found.contains(t)).toList());
  }

  @Test
  void required_extensions_are_installed() {
    // btree_gist is what makes the exclusion constraint on student_group_assignment possible.
    var count =
        jdbcTemplate.queryForObject(
            "select count(*) from pg_extension where extname in ('pgcrypto', 'btree_gist')",
            Integer.class);

    assertEquals(2, count);
  }

  // --- student_group_assignment: no overlapping periods ---------------------

  @Test
  void consecutive_group_assignments_are_accepted() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var k1 = insertGroup(promotion, null);
    var k2 = insertGroup(promotion, null);

    assignGroup(student, k1, "2025-09-01", "2025-11-14");

    assertDoesNotThrow(() -> assignGroup(student, k2, "2025-11-15", null));
  }

  @Test
  void overlapping_group_assignments_are_rejected() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var k1 = insertGroup(promotion, null);
    var k2 = insertGroup(promotion, null);

    assignGroup(student, k1, "2025-09-01", "2025-11-30");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> assignGroup(student, k2, "2025-11-15", "2025-12-31"));
  }

  @Test
  void a_student_cannot_have_two_open_ended_assignments() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var k1 = insertGroup(promotion, null);
    var k2 = insertGroup(promotion, null);

    assignGroup(student, k1, "2025-09-01", null);

    assertThrows(
        DataIntegrityViolationException.class, () -> assignGroup(student, k2, "2026-02-10", null));
  }

  @Test
  void two_students_may_share_the_same_period_in_the_same_group() {
    // The exclusion constraint is scoped per student, not per group.
    var promotion = insertPromotion();
    var group = insertGroup(promotion, null);
    var jean = insertStudent(promotion);
    var alice = insertStudent(promotion);

    assignGroup(jean, group, "2025-09-01", null);

    assertDoesNotThrow(() -> assignGroup(alice, group, "2025-09-01", null));
  }

  // --- student_group: track consistency ------------------------------------

  @Test
  void a_common_core_group_needs_no_track() {
    // MATCH SIMPLE: a null track_id satisfies the composite foreign key.
    var promotion = insertPromotion();

    assertDoesNotThrow(() -> insertGroup(promotion, null));
  }

  @Test
  void a_group_can_carry_a_track_its_promotion_opens() {
    var promotion = insertPromotion();
    var track = insertTrack();
    openTrack(promotion, track);

    assertDoesNotThrow(() -> insertGroup(promotion, track));
  }

  @Test
  void a_group_cannot_carry_a_track_its_promotion_does_not_open() {
    var promotion = insertPromotion();
    var trackOpenedByNobody = insertTrack();

    assertThrows(
        DataIntegrityViolationException.class, () -> insertGroup(promotion, trackOpenedByNobody));
  }

  // --- grade and its history -----------------------------------------------

  @Test
  void a_grade_value_stays_within_zero_and_twenty() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var exam = insertExam(insertCourse(semester("S1", 1, 1, true)));

    assertThrows(DataIntegrityViolationException.class, () -> insertGrade(student, exam, 20.5));
  }

  @Test
  void a_student_has_at_most_one_grade_per_exam() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var exam = insertExam(insertCourse(semester("S1", 1, 1, true)));
    insertGrade(student, exam, 12);

    assertThrows(DataIntegrityViolationException.class, () -> insertGrade(student, exam, 14));
  }

  @Test
  void a_grade_change_without_a_reason_is_impossible() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var exam = insertExam(insertCourse(semester("S1", 1, 1, true)));
    var grade = insertGrade(student, exam, 10);
    var author = insertUser();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "insert into grade_history (grade_id, old_value, new_value, reason_type, reason,"
                    + " changed_by) values (?, 10, 14, 'CLAIM', '   ', ?)",
                grade,
                author));
  }

  @Test
  void a_grade_change_must_actually_change_the_value() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var exam = insertExam(insertCourse(semester("S1", 1, 1, true)));
    var grade = insertGrade(student, exam, 10);
    var author = insertUser();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "insert into grade_history (grade_id, old_value, new_value, reason_type, reason,"
                    + " changed_by) values (?, 14, 14, 'CORRECTION', 'same value', ?)",
                grade,
                author));
  }

  // --- student_track_choice -------------------------------------------------

  @Test
  void a_student_cannot_choose_two_tracks_from_the_same_semester() {
    var promotion = insertPromotion();
    var student = insertStudent(promotion);
    var el = insertTrack();
    var tn = insertTrack();
    var s4 = semester("S4", 4, 2, false);

    jdbcTemplate.update(
        "insert into student_track_choice (student_id, track_id, from_semester_id)"
            + " values (?, ?, ?)",
        student,
        el,
        s4);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "insert into student_track_choice (student_id, track_id, from_semester_id)"
                    + " values (?, ?, ?)",
                student,
                tn,
                s4));
  }

  // --- promotion ------------------------------------------------------------

  @Test
  void a_promotion_ends_after_it_starts() {
    assertThrows(DataIntegrityViolationException.class, () -> insertPromotion(2028, 2025));
  }
}
