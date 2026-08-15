package com.exam.hei.conf;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hands out references that are unique by construction, for the columns too narrow to hold a random
 * one.
 *
 * <p>{@code promotion.ref} is {@code varchar(5)} because the business says so: a cohort is named
 * K22, H23. Five random hex characters only span about a million values, and a full suite run
 * creates several hundred promotions against a Postgres container shared by every test class — the
 * birthday bound puts that at a few percent chance of a UNIQUE violation per run. It is exactly the
 * kind of failure that passes locally and reddens a pipeline once a fortnight, so this replaces
 * chance with counting.
 *
 * <p>A worker id and a counter, both base 36, packed into the five characters available: two for
 * the JVM and three for the sequence. Gradle forks several test JVMs — see {@code maxParallelForks}
 * — and gives each one a distinct {@code org.gradle.test.worker}, so within a worker the counter
 * never repeats and two workers never share a prefix.
 *
 * <p>The two bounds this relies on: fewer than 1296 workers whose ids fall in the same window of
 * 1296, and fewer than 46 656 promotions per worker. A run creates a handful of workers and a few
 * hundred promotions, so both hold by three orders of magnitude — and unlike the random scheme it
 * replaces, exceeding them would be a visible, reproducible failure rather than an occasional one.
 */
public final class TestRefs {

  private static final int WORKER_SLOTS = 36 * 36;
  private static final int SEQUENCE_SLOTS = 36 * 36 * 36;

  private static final int WORKER =
      Integer.parseInt(System.getProperty("org.gradle.test.worker", "0")) % WORKER_SLOTS;

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private TestRefs() {}

  /** Exactly five characters, the width {@code promotion.ref} allows. */
  public static String promotionRef() {
    var sequence = SEQUENCE.getAndIncrement() % SEQUENCE_SLOTS;
    return (padded(WORKER, 2) + padded(sequence, 3)).toUpperCase();
  }

  private static String padded(int value, int width) {
    var base36 = Integer.toString(value, 36);
    return "0".repeat(width - base36.length()) + base36;
  }
}
