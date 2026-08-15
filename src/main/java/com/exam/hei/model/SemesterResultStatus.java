package com.exam.hei.model;

/**
 * Whether a semester's academic context could be resolved, mirroring the {@code
 * SemesterResultStatus} enumeration of doc/api.yml.
 *
 * <p>{@code TRACK_NOT_SELECTED} is not a neutral absence: a semester in that state is not
 * evaluable, and must never be confused with one that was evaluated and then failed for lack of
 * credits.
 */
public enum SemesterResultStatus {
  EVALUATED,
  TRACK_NOT_SELECTED
}
