package com.exam.hei.model;

import com.exam.hei.repository.model.Track;
import java.math.BigDecimal;

/**
 * One row of the graduate list of a promotion, ranked by decreasing general average.
 *
 * <p>{@code rank} is a competition ranking: tied students share the same rank, and the next one
 * skips ahead by the number of ties (1, 1, 3, 4 — never 1, 1, 2, 3). Ties are ordered, for display
 * only, by last name then first name; that order never changes the rank value itself.
 *
 * <p>{@code track} is the exit track, null only for a diploma predating any track (never in
 * practice: graduating requires S4 to S6 validated, which requires a track choice).
 */
public record Graduate(
    int rank,
    String std,
    String lastName,
    String firstName,
    BigDecimal generalAverage,
    Track track) {}
