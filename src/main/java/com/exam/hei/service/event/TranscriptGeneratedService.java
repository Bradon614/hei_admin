package com.exam.hei.service.event;

import com.exam.hei.endpoint.event.model.TranscriptGenerated;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.mail.Email;
import com.exam.hei.mail.Mailer;
import com.exam.hei.repository.TranscriptRequestRepository;
import com.exam.hei.repository.model.TranscriptRequest;
import com.exam.hei.repository.model.TranscriptStatus;
import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@AllArgsConstructor
@Slf4j
public class TranscriptGeneratedService implements Consumer<TranscriptGenerated> {
  static final Duration LINK_VALIDITY = Duration.ofDays(7);

  private static final String TEMPLATE = "transcript-email";
  private static final String SUBJECT = "Your academic transcript";
  private static final String SENDING_FAILED = "The transcript could not be emailed";

  private final TranscriptRequestRepository transcriptRequestRepository;
  private final BucketComponent bucketComponent;
  private final Mailer mailer;
  private final SpringTemplateEngine templateEngine;

  @Override
  public void accept(TranscriptGenerated event) {
    var transcriptRequest = transcriptRequestRepository.findById(event.getTranscriptRequestId());
    if (transcriptRequest.isEmpty()) {
      log.error("Transcript request {} no longer exists, dropping", event.getTranscriptRequestId());
      return;
    }

    var request = transcriptRequest.get();
    if (request.getStatus() == TranscriptStatus.SENT) {
      log.info("Transcript request {} was already sent, skipping", request.getId());
      return;
    }

    try {
      send(request);
    } catch (RuntimeException e) {
      log.error("Transcript request {} could not be emailed", request.getId(), e);
      request.setStatus(TranscriptStatus.FAILED);
      request.setErrorMessage(SENDING_FAILED);
      transcriptRequestRepository.save(request);
    }
  }

  private void send(TranscriptRequest request) {
    var fileUrl = bucketComponent.presign(request.getS3Key(), LINK_VALIDITY).toString();
    mailer.accept(
        new Email(
            recipient(request), List.of(), List.of(), SUBJECT, body(request, fileUrl), List.of()));

    request.setFileUrl(fileUrl);
    request.setStatus(TranscriptStatus.SENT);
    request.setSentAt(Instant.now());
    transcriptRequestRepository.save(request);
  }

  private InternetAddress recipient(TranscriptRequest request) {
    try {
      return new InternetAddress(request.getStudent().getEmail());
    } catch (jakarta.mail.internet.AddressException e) {
      throw new IllegalStateException("Student email is not a usable address", e);
    }
  }

  private String body(TranscriptRequest request, String fileUrl) {
    var context = new Context();
    context.setVariable("firstName", request.getStudent().getFirstName());
    context.setVariable("studentRef", request.getStudent().getRef());
    context.setVariable(
        "scope", request.getSemester() == null ? null : request.getSemester().getRef().name());
    context.setVariable("fileUrl", fileUrl);
    context.setVariable("expiresInDays", LINK_VALIDITY.toDays());
    return templateEngine.process(TEMPLATE, context);
  }
}
