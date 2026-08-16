package com.exam.hei.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.conf.FacadeIT;
import com.exam.hei.conf.TestRefs;
import com.exam.hei.endpoint.event.consumer.EventServiceInvoker;
import com.exam.hei.endpoint.event.consumer.model.TypedEvent;
import com.exam.hei.endpoint.event.model.TranscriptGenerated;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.mail.Email;
import com.exam.hei.mail.Mailer;
import com.exam.hei.repository.AppUserRepository;
import com.exam.hei.repository.PromotionRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TranscriptRequestRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptRequest;
import com.exam.hei.repository.model.TranscriptStatus;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class TranscriptGeneratedConsumerIT extends FacadeIT {
  @Autowired EventServiceInvoker eventServiceInvoker;
  @Autowired TranscriptRequestRepository transcriptRequestRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired AppUserRepository appUserRepository;

  @MockBean Mailer mailer;

  @MockBean BucketComponent bucketComponent;

  private static final String PRESIGNED =
      "https://bucket.s3.eu-west-3.amazonaws.com/transcripts/x.pdf?X-Amz-Signature=abc";

  @BeforeEach
  void presigningWorks() throws Exception {
    when(bucketComponent.presign(anyString(), any())).thenReturn(URI.create(PRESIGNED).toURL());
  }

  private static String rand(int length) {
    return UUID.randomUUID().toString().replace("-", "").substring(0, length);
  }

  private Student student() {
    var account =
        appUserRepository.save(
            AppUser.builder()
                .email(rand(12) + "@hei.test")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    var promotion =
        promotionRepository.save(
            Promotion.builder()
                .ref(TestRefs.promotionRef())
                .name("Promotion under test")
                .startYear(2025)
                .endYear(2028)
                .build());
    return studentRepository.save(
        Student.builder()
            .ref(rand(20))
            .firstName("Jean")
            .lastName("Rakoto")
            .email(rand(12) + "@hei.test")
            .entranceDate(LocalDate.of(2025, 9, 1))
            .promotion(promotion)
            .user(account)
            .build());
  }

  private TranscriptRequest generatedRequest() {
    var student = student();
    return transcriptRequestRepository.save(
        TranscriptRequest.builder()
            .student(student)
            .status(TranscriptStatus.GENERATED)
            .s3Key("transcripts/" + student.getId() + "/" + UUID.randomUUID() + ".pdf")
            .requestedBy(
                appUserRepository.save(
                    AppUser.builder()
                        .email(rand(12) + "@hei.test")
                        .passwordHash("hash")
                        .role(Role.ADMIN)
                        .build()))
            .build());
  }

  private void dispatch(TranscriptGenerated event) {
    eventServiceInvoker.accept(new TypedEvent(TranscriptGenerated.class.getTypeName(), event));
  }

  @Test
  void the_invoker_routes_the_event_to_the_transcript_consumer() {
    var request = generatedRequest();

    dispatch(TranscriptGenerated.builder().transcriptRequestId(request.getId()).build());

    var reloaded = transcriptRequestRepository.findById(request.getId()).orElseThrow();
    assertEquals(TranscriptStatus.SENT, reloaded.getStatus());
    assertEquals(PRESIGNED, reloaded.getFileUrl());
    assertNotNull(reloaded.getSentAt());
  }

  @Test
  void the_student_receives_the_link() {
    var request = generatedRequest();
    var expectedRecipient = request.getStudent().getEmail();

    dispatch(TranscriptGenerated.builder().transcriptRequestId(request.getId()).build());

    var captor = ArgumentCaptor.forClass(Email.class);
    verify(mailer).accept(captor.capture());
    var email = captor.getValue();
    assertEquals(expectedRecipient, email.to().getAddress());
    assertTrue(email.htmlBody().contains(PRESIGNED), "body was " + email.htmlBody());
    assertTrue(email.htmlBody().contains("Jean"), "body was " + email.htmlBody());
  }

  @Test
  void the_email_template_renders_through_the_spring_engine() {
    var request = generatedRequest();

    dispatch(TranscriptGenerated.builder().transcriptRequestId(request.getId()).build());

    var captor = ArgumentCaptor.forClass(Email.class);
    verify(mailer).accept(captor.capture());
    assertTrue(captor.getValue().htmlBody().contains("Download my transcript"));
    assertTrue(captor.getValue().htmlBody().contains("7 days"), "link validity must be stated");
  }

  @Test
  void an_event_whose_service_does_not_exist_is_rejected_loudly() {
    assertThrows(
        RuntimeException.class,
        () -> eventServiceInvoker.accept(new TypedEvent("com.exam.hei.Nope", null)));
  }
}
