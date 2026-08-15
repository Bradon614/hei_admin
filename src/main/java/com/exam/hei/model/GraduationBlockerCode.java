package com.exam.hei.model;

/**
 * Precise cause of a semester not being validated, mirroring the {@code GraduationBlockerCode}
 * enumeration of doc/api.yml.
 *
 * <p>Kept apart from {@code SEMESTER_NOT_VALIDATED} deliberately: a student who never chose a track
 * must never show up as "not graduated for insufficient credits".
 */
public enum GraduationBlockerCode {
  SEMESTER_NOT_VALIDATED,
  TRACK_NOT_SELECTED
}
