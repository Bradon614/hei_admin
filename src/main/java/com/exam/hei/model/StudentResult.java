package com.exam.hei.model;

import com.exam.hei.repository.model.Student;
import java.math.BigDecimal;
import java.util.List;

public record StudentResult(
    Student student,
    List<SemesterResult> semesterResults,
    int totalObtainedCredits,
    BigDecimal generalAverage,
    boolean graduated,
    List<GraduationBlocker> blockers) {}
