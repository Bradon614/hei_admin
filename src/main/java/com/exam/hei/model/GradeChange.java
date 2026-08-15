package com.exam.hei.model;

import com.exam.hei.repository.model.GradeChangeReasonType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One element of {@code PUT /exams/{id}/grades}: entry or update of a single student's grade.
 *
 * <p>Not a persisted entity: {@code GradeService} turns it into a {@code Grade} write and a {@code
 * GradeHistory} row, neither of which it maps to directly.
 */
public record GradeChange(
    UUID studentId, BigDecimal value, GradeChangeReasonType reasonType, String reason) {}
