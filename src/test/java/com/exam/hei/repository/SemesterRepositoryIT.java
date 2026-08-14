package com.exam.hei.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Reads the reference data seeded by the migrations through the entity mapping. */
class SemesterRepositoryIT extends FacadeIT {

  @Autowired SemesterRepository semesterRepository;

  @Test
  void the_six_semesters_are_readable_in_order() {
    var refs = semesterRepository.findAllByOrderBySemOrderAsc().stream().map(Semester::getRef);

    assertEquals(
        List.of(
            SemesterRef.S1,
            SemesterRef.S2,
            SemesterRef.S3,
            SemesterRef.S4,
            SemesterRef.S5,
            SemesterRef.S6),
        refs.toList());
  }

  @Test
  void a_semester_is_findable_by_its_ref() {
    var s4 = semesterRepository.findByRef(SemesterRef.S4).orElseThrow();

    assertEquals(4, s4.getSemOrder());
    assertEquals(2, s4.getYearNumber());
    assertEquals(30, s4.getRequiredCredits());
  }

  @Test
  void the_common_core_flag_survives_the_mapping() {
    assertTrue(semesterRepository.findByRef(SemesterRef.S3).orElseThrow().isCommonCore());
    assertFalse(semesterRepository.findByRef(SemesterRef.S4).orElseThrow().isCommonCore());
  }

  @Test
  void the_semester_a_track_is_chosen_at_is_read_from_the_data() {
    // The rule says "the first non common core semester". Nothing anywhere says "S4".
    var first = semesterRepository.findFirstByCommonCoreFalseOrderBySemOrderAsc().orElseThrow();

    assertEquals(SemesterRef.S4, first.getRef());
  }
}
