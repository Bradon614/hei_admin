package com.exam.hei.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.event.model.TranscriptGenerated;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.mail.Email;
import com.exam.hei.mail.Mailer;
import com.exam.hei.repository.TranscriptRequestRepository;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptRequest;
import com.exam.hei.repository.model.TranscriptStatus;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;

class TranscriptGeneratedServiceTest {

  private final TranscriptRequestRepository transcriptRequestRepository =
      mock(TranscriptRequestRepository.class);
  private final BucketComponent bucketComponent = mock(BucketComponent.class);

  /** Never a real one: sending would call SES, which costs money and needs a verified identity. */
  private final Mailer mailer = mock(Mailer.class);

  private final SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);

  private final TranscriptGeneratedService subject =
      new TranscriptGeneratedService(
          transcriptRequestRepository, bucketComponent, mailer, templateEngine);

  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final String PRESIGNED =
      "https://bucket.s3.eu-west-3.amazonaws.com/transcripts/x.pdf?X-Amz-Signature=abc";

  private static Student student() {
    return Student.builder()
        .id(UUID.randomUUID())
        .ref("STD22045")
        .firstName("Jean")
        .lastName("Rakoto")
        .email("jean.rakoto@hei.school")
        .build();
  }

  private static TranscriptRequest request(TranscriptStatus status) {
    return TranscriptRequest.builder()
        .id(REQUEST_ID)
        .student(student())
        .status(status)
        .s3Key("transcripts/abc/def.pdf")
        .build();
  }

  private static TranscriptGenerated event() {
    return TranscriptGenerated.builder().transcriptRequestId(REQUEST_ID).build();
  }

  private void generatedRequest(TranscriptRequest request) throws Exception {
    when(transcriptRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    when(bucketComponent.presign(anyString(), any())).thenReturn(URI.create(PRESIGNED).toURL());
    when(templateEngine.process(anyString(), any(org.thymeleaf.context.IContext.class)))
        .thenReturn("<html>body</html>");
    when(transcriptRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  private Email sentEmail() {
    var captor = ArgumentCaptor.forClass(Email.class);
    verify(mailer).accept(captor.capture());
    return captor.getValue();
  }

  // --- the successful asynchronous half -------------------------------------------

  @Test
  void a_generated_request_ends_sent() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    generatedRequest(request);

    subject.accept(event());

    assertEquals(TranscriptStatus.SENT, request.getStatus());
    assertNotNull(request.getSentAt());
  }

  @Test
  void the_presigned_link_is_recorded_as_the_file_url() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    generatedRequest(request);

    subject.accept(event());

    assertEquals(PRESIGNED, request.getFileUrl());
  }

  @Test
  void the_link_is_presigned_against_the_stored_key_for_seven_days() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    generatedRequest(request);

    subject.accept(event());

    verify(bucketComponent)
        .presign("transcripts/abc/def.pdf", TranscriptGeneratedService.LINK_VALIDITY);
    assertEquals(7, TranscriptGeneratedService.LINK_VALIDITY.toDays());
  }

  @Test
  void the_email_goes_to_the_student() throws Exception {
    generatedRequest(request(TranscriptStatus.GENERATED));

    subject.accept(event());

    assertEquals("jean.rakoto@hei.school", sentEmail().to().getAddress());
  }

  @Test
  void the_email_carries_a_body_and_no_attachment() throws Exception {
    // The specification has the consumer email a link; attaching the PDF would leave a transcript
    // sitting in a mailbox for good.
    generatedRequest(request(TranscriptStatus.GENERATED));

    subject.accept(event());

    var email = sentEmail();
    assertTrue(email.attachments().isEmpty());
    assertNotNull(email.htmlBody());
    assertEquals("Your academic transcript", email.subject());
  }

  @Test
  void a_single_semester_request_names_its_scope_in_the_body() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    request.setSemester(Semester.builder().ref(SemesterRef.S5).build());
    generatedRequest(request);

    subject.accept(event());

    var context = ArgumentCaptor.forClass(org.thymeleaf.context.IContext.class);
    verify(templateEngine).process(anyString(), context.capture());
    assertEquals("S5", context.getValue().getVariable("scope"));
  }

  @Test
  void a_full_curriculum_request_names_no_scope() throws Exception {
    generatedRequest(request(TranscriptStatus.GENERATED));

    subject.accept(event());

    var context = ArgumentCaptor.forClass(org.thymeleaf.context.IContext.class);
    verify(templateEngine).process(anyString(), context.capture());
    assertNull(context.getValue().getVariable("scope"));
  }

  // --- redelivery -----------------------------------------------------------------

  @Test
  void an_already_sent_request_is_not_emailed_twice() throws Exception {
    // SQS delivers at least once. Without this guard a redelivery mails the student again.
    var request = request(TranscriptStatus.SENT);
    when(transcriptRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

    subject.accept(event());

    verify(mailer, never()).accept(any());
    verify(bucketComponent, never()).presign(anyString(), any());
    verify(transcriptRequestRepository, never()).save(any());
  }

  // --- failures -------------------------------------------------------------------

  @Test
  void a_vanished_request_is_dropped_rather_than_retried_forever() {
    when(transcriptRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

    subject.accept(event());

    verify(mailer, never()).accept(any());
    verify(transcriptRequestRepository, never()).save(any());
  }

  @Test
  void a_sending_failure_ends_failed() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    generatedRequest(request);
    doThrow(new RuntimeException("SES rejected the message")).when(mailer).accept(any());

    subject.accept(event());

    assertEquals(TranscriptStatus.FAILED, request.getStatus());
    assertNull(request.getSentAt());
  }

  @Test
  void a_sending_failure_never_leaks_the_underlying_error() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    generatedRequest(request);
    doThrow(new RuntimeException("software.amazon.awssdk MessageRejected: identity not verified"))
        .when(mailer)
        .accept(any());

    subject.accept(event());

    assertEquals("The transcript could not be emailed", request.getErrorMessage());
    assertTrue(!request.getErrorMessage().contains("awssdk"));
  }

  @Test
  void a_presigning_failure_ends_failed_without_sending_anything() throws Exception {
    var request = request(TranscriptStatus.GENERATED);
    generatedRequest(request);
    when(bucketComponent.presign(anyString(), any()))
        .thenThrow(new RuntimeException("bucket unreachable"));

    subject.accept(event());

    assertEquals(TranscriptStatus.FAILED, request.getStatus());
    verify(mailer, never()).accept(any());
  }
}
