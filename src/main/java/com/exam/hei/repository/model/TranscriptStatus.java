package com.exam.hei.repository.model;

/**
 * Lifecycle of a transcript request, mirroring the {@code TranscriptStatus} enumeration of
 * doc/api.yml.
 *
 * <p>The two middle values are what makes the asynchronous half observable: {@code GENERATED} ends
 * the synchronous work (PDF built, uploaded, event published) and {@code SENT} ends the
 * asynchronous one (email delivered by the consumer).
 */
public enum TranscriptStatus {
  PENDING,
  GENERATED,
  SENT,
  FAILED
}
