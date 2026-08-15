package com.exam.hei.repository.model;

/**
 * Category of the reason behind a grade entry or update, mirroring the {@code
 * GradeChangeReasonType} enumeration of doc/api.yml.
 *
 * <p>{@code CREATION} is reserved for the first entry of a grade: never used again once one exists,
 * and mandatory the first time.
 */
public enum GradeChangeReasonType {
  CREATION,
  CLAIM,
  INPUT_ERROR,
  CORRECTION,
  OTHER
}
