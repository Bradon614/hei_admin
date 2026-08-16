package com.exam.hei.model;

import com.exam.hei.repository.model.GradeChangeReasonType;
import java.math.BigDecimal;
import java.util.UUID;

public record GradeChange(
    UUID studentId, BigDecimal value, GradeChangeReasonType reasonType, String reason) {}
