package com.exam.hei.model;

import com.exam.hei.repository.model.SemesterRef;

/**
 * Precise cause, attached to a semester, of a diploma not obtained.
 *
 * <p>Lets a promotion listing exclude a student from the graduate list with the exact reason,
 * rather than a generic "insufficient credits".
 */
public record GraduationBlocker(
    GraduationBlockerCode code, SemesterRef semesterRef, String message) {}
