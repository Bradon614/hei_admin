package com.exam.hei.model;

import com.exam.hei.repository.model.Course;
import java.math.BigDecimal;

public record CourseResult(
    Course course, BigDecimal finalGrade, boolean validated, int obtainedCredits) {}
