package com.exam.hei.model;

import com.exam.hei.repository.model.Student;
import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a student across the three years, computed dynamically: nothing here is persisted.
 *
 * <p>Graduated if and only if the six semesters S1 to S6 are validated. A general average above 10
 * is never enough: a single non validated semester makes the student a non graduate.
 *
 * <p>{@code generalAverage = sum(final_grade x credits) / sum(credits)}, over the applicable
 * courses from S1 to S6 that actually carry a grade (a course without an exam contributes to
 * neither side, rather than being treated as a zero). Weighting by credits is an explicit project
 * convention, not something the assignment mandates.
 */
public record StudentResult(
    Student student,
    List<SemesterResult> semesterResults,
    int totalObtainedCredits,
    BigDecimal generalAverage,
    boolean graduated,
    List<GraduationBlocker> blockers) {}
