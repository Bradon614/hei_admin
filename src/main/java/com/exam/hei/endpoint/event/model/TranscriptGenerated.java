package com.exam.hei.endpoint.event.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Crosses the synchronous / asynchronous boundary: the PDF is on S3 and the transcript request is
 * GENERATED, so someone may now email it.
 *
 * <p>Deliberately in this package and not another: {@code EventServiceInvoker} only scans {@code
 * com.exam.hei.endpoint.event.model}, and resolves the consumer by name as {@code
 * com.exam.hei.service.event.TranscriptGeneratedService}. Renaming or moving either side breaks the
 * routing at runtime rather than at compile time.
 *
 * <p>Carries the request id alone. Everything else — student, semester, bucket key — is already a
 * row away, and copying it into the payload would let the two drift apart between publication and
 * consumption.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@EqualsAndHashCode(callSuper = false)
@ToString
public class TranscriptGenerated extends PojaEvent {

  @JsonProperty("transcriptRequestId")
  private UUID transcriptRequestId;

  @Override
  public Duration maxConsumerDuration() {
    // Presigning the object and handing the mail to SES; no PDF work happens on this side.
    return Duration.ofSeconds(30);
  }

  @Override
  public Duration maxConsumerBackoffBetweenRetries() {
    return Duration.ofSeconds(60);
  }
}
