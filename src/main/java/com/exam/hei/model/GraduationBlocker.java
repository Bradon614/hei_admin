package com.exam.hei.model;

import com.exam.hei.repository.model.SemesterRef;

public record GraduationBlocker(
    GraduationBlockerCode code, SemesterRef semesterRef, String message) {}
