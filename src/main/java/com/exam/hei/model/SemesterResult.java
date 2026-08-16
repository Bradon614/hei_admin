package com.exam.hei.model;

import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.Track;
import java.util.List;

public record SemesterResult(
    Semester semester,
    SemesterResultStatus status,
    Track track,
    int obtainedCredits,
    int requiredCredits,
    boolean validated,
    List<CourseResult> courseResults) {}
