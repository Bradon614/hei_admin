package com.exam.hei.model;

import com.exam.hei.repository.model.Track;
import java.math.BigDecimal;

public record Graduate(
    int rank,
    String std,
    String lastName,
    String firstName,
    BigDecimal generalAverage,
    Track track) {}
