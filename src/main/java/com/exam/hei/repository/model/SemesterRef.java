package com.exam.hei.repository.model;

/**
 * The six semesters of the curriculum, mirroring the {@code SemesterRef} enum of doc/api.yml.
 *
 * <p>Whether a semester is common core is not encoded here: it is reference data carried by the
 * {@code common_core} column, so that the semester the track choice starts at never becomes a
 * constant written in the business code.
 */
public enum SemesterRef {
  S1,
  S2,
  S3,
  S4,
  S5,
  S6
}
