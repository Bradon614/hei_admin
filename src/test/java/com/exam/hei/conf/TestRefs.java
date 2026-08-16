package com.exam.hei.conf;

import java.util.concurrent.atomic.AtomicInteger;

public final class TestRefs {
  private static final int WORKER_SLOTS = 36 * 36;
  private static final int SEQUENCE_SLOTS = 36 * 36 * 36;

  private static final int WORKER =
      Integer.parseInt(System.getProperty("org.gradle.test.worker", "0")) % WORKER_SLOTS;

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private TestRefs() {}

  public static String promotionRef() {
    var sequence = SEQUENCE.getAndIncrement() % SEQUENCE_SLOTS;
    return (padded(WORKER, 2) + padded(sequence, 3)).toUpperCase();
  }

  private static String padded(int value, int width) {
    var base36 = Integer.toString(value, 36);
    return "0".repeat(width - base36.length()) + base36;
  }
}
