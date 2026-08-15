package com.exam.hei.model;

import com.exam.hei.repository.model.Course;
import java.math.BigDecimal;

/**
 * Result of a student for one course applicable to their academic context at that course's
 * semester.
 *
 * <p>{@code finalGrade = sum(grade_i x coefficient_i) / sum(coefficient_i)} over every exam of the
 * course; a missing grade counts as 0 in the numerator, its coefficient staying in the denominator.
 * Null when the course has no exam at all, which {@code ResultService} tells apart from a genuine
 * zero. Validated at 10 and above, which grants the course's credits.
 */
public record CourseResult(
    Course course, BigDecimal finalGrade, boolean validated, int obtainedCredits) {}
