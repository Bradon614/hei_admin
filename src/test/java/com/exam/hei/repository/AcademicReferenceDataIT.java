package com.exam.hei.repository;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.exam.hei.conf.FacadeIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AcademicReferenceDataIT extends FacadeIT {
  @Autowired JdbcTemplate jdbcTemplate;

  private List<String> semesterRefs(String whereClause) {
    return jdbcTemplate.queryForList(
        "select ref from semester where " + whereClause + " order by sem_order", String.class);
  }

  @Test
  void the_curriculum_holds_exactly_six_semesters() {
    assertEquals(List.of("S1", "S2", "S3", "S4", "S5", "S6"), semesterRefs("true"));
  }

  @Test
  void semester_ranks_run_from_one_to_six_without_a_gap() {
    var ranks =
        jdbcTemplate.queryForList(
            "select sem_order from semester order by sem_order", Integer.class);

    assertEquals(List.of(1, 2, 3, 4, 5, 6), ranks);
  }

  @Test
  void the_first_three_semesters_are_common_core() {
    assertEquals(List.of("S1", "S2", "S3"), semesterRefs("common_core"));
  }

  @Test
  void the_last_three_semesters_carry_a_track() {
    assertEquals(List.of("S4", "S5", "S6"), semesterRefs("not common_core"));
  }

  @Test
  void a_track_must_be_chosen_from_s4_on() {
    var firstTrackSemester =
        jdbcTemplate.queryForObject(
            "select ref from semester where not common_core order by sem_order limit 1",
            String.class);

    assertEquals("S4", firstTrackSemester);
  }

  @Test
  void each_semester_is_worth_thirty_credits_for_a_total_of_one_hundred_and_eighty() {
    var perSemester =
        jdbcTemplate.queryForList("select distinct required_credits from semester", Integer.class);
    var total =
        jdbcTemplate.queryForObject("select sum(required_credits) from semester", Integer.class);

    assertEquals(List.of(30), perSemester);
    assertEquals(180, total);
  }

  @Test
  void the_six_semesters_are_spread_over_three_years() {
    var yearByRef =
        jdbcTemplate
            .queryForList("select ref, year_number from semester order by sem_order")
            .stream()
            .collect(
                toMap(row -> (String) row.get("ref"), row -> (Integer) row.get("year_number")));

    assertEquals(Map.of("S1", 1, "S2", 1, "S3", 2, "S4", 2, "S5", 3, "S6", 3), yearByRef);
  }

  @Test
  void both_hei_tracks_are_seeded() {
    var seeded =
        jdbcTemplate.queryForList(
            "select code from track where code in ('EL', 'TN') order by code", String.class);

    assertEquals(List.of("EL", "TN"), seeded);
  }

  @Test
  void seeded_tracks_carry_their_hei_name() {
    var names =
        jdbcTemplate.queryForList(
            "select name from track where code in ('EL', 'TN') order by code", String.class);

    assertEquals(List.of("Software Ecosystem", "Digital Transformation"), names);
  }
}
