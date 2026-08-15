package com.exam.hei.model;

import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.Track;
import java.util.List;

/**
 * Result of a student for one semester.
 *
 * <p>A semester is validated once obtained credits reach the required credits (30), counted only
 * over the courses applicable to that semester's academic context: common core courses in S1-S3,
 * common courses plus the chosen track's in S4-S6. An EL student can therefore never fail because
 * of a TN-only course.
 *
 * <p>{@code status} is {@code TRACK_NOT_SELECTED} when the semester is not common core and no track
 * choice covers it: the semester is then not evaluable, {@code obtainedCredits} is 0, {@code
 * validated} is false and {@code courseResults} is empty. This is what keeps a student who never
 * chose from being indistinguishable from one who simply failed.
 */
public record SemesterResult(
    Semester semester,
    SemesterResultStatus status,
    Track track,
    int obtainedCredits,
    int requiredCredits,
    boolean validated,
    List<CourseResult> courseResults) {}
