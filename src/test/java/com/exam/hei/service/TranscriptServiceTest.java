package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exam.hei.endpoint.event.EventProducer;
import com.exam.hei.endpoint.event.model.TranscriptGenerated;
import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.file.hash.FileHash;
import com.exam.hei.file.hash.FileHashAlgorithm;
import com.exam.hei.model.StudentResult;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TranscriptRequestRepository;
import com.exam.hei.repository.model.AppUser;
import com.exam.hei.repository.model.Role;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptRequest;
import com.exam.hei.repository.model.TranscriptStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TranscriptServiceTest {
  private final TranscriptRequestRepository transcriptRequestRepository =
      mock(TranscriptRequestRepository.class);
  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final SemesterRepository semesterRepository = mock(SemesterRepository.class);
  private final ResultService resultService = mock(ResultService.class);
  private final TranscriptPdfGenerator pdfGenerator = mock(TranscriptPdfGenerator.class);
  private final BucketComponent bucketComponent = mock(BucketComponent.class);

  @SuppressWarnings("unchecked")
  private final EventProducer<TranscriptGenerated> eventProducer = mock(EventProducer.class);

  private final StudentAuthorizer studentAuthorizer = mock(StudentAuthorizer.class);
  private final AuthenticatedResourceProvider authenticatedResourceProvider =
      mock(AuthenticatedResourceProvider.class);

  private final TranscriptService subject =
      new TranscriptService(
          transcriptRequestRepository,
          studentRepository,
          semesterRepository,
          resultService,
          pdfGenerator,
          bucketComponent,
          eventProducer,
          studentAuthorizer,
          authenticatedResourceProvider);

  private static final UUID STUDENT_ID = UUID.randomUUID();
  private static final Student STUDENT = Student.builder().id(STUDENT_ID).ref("STD1").build();
  private static final AppUser CALLER =
      AppUser.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
  private static final byte[] PDF = "%PDF-1.4 pretend".getBytes();

  private void happyPath() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(authenticatedResourceProvider.getAuthenticatedUser()).thenReturn(CALLER);
    when(resultService.resultOf(STUDENT_ID))
        .thenReturn(
            new StudentResult(STUDENT, List.of(), 180, new BigDecimal("14.00"), true, List.of()));
    when(pdfGenerator.generate(any(), any())).thenReturn(PDF);
    when(bucketComponent.upload(any(), anyString()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "hash"));

    when(transcriptRequestRepository.save(any()))
        .thenAnswer(
            invocation -> {
              TranscriptRequest saved = invocation.getArgument(0);
              if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
              }
              return saved;
            });
  }

  private TranscriptRequest savedState() {
    var captor = ArgumentCaptor.forClass(TranscriptRequest.class);
    verify(transcriptRequestRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void a_successful_request_ends_generated() {
    happyPath();

    var result = subject.request(STUDENT_ID, null);

    assertEquals(TranscriptStatus.GENERATED, result.getStatus());
    assertNotNull(result.getGeneratedAt());
  }

  @Test
  void the_bucket_key_is_recorded() {
    happyPath();

    var result = subject.request(STUDENT_ID, null);

    assertNotNull(result.getS3Key());
    assertTrue(result.getS3Key().startsWith("transcripts/" + STUDENT_ID + "/"), result.getS3Key());
    assertTrue(result.getS3Key().endsWith(".pdf"), result.getS3Key());
  }

  @Test
  void the_file_url_stays_null_until_the_consumer_fills_it() {
    happyPath();

    var result = subject.request(STUDENT_ID, null);

    assertNull(result.getFileUrl());
    assertNull(result.getSentAt());
  }

  @Test
  void the_uploaded_bytes_are_the_generated_pdf() {
    happyPath();

    subject.request(STUDENT_ID, null);

    var file = ArgumentCaptor.forClass(java.io.File.class);
    verify(bucketComponent).upload(file.capture(), anyString());
    assertTrue(file.getValue().getName().endsWith(".pdf"));
  }

  @Test
  void the_event_carries_the_request_id() {
    happyPath();

    var result = subject.request(STUDENT_ID, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<TranscriptGenerated>> captor =
        ArgumentCaptor.forClass(Collection.class);
    verify(eventProducer).accept(captor.capture());
    assertEquals(1, captor.getValue().size());
    assertEquals(result.getId(), captor.getValue().iterator().next().getTranscriptRequestId());
  }

  @Test
  void the_event_is_published_only_after_the_upload_succeeded() {
    happyPath();

    subject.request(STUDENT_ID, null);

    var order = inOrder(bucketComponent, eventProducer);
    order.verify(bucketComponent).upload(any(), anyString());
    order.verify(eventProducer).accept(any());
  }

  @Test
  void a_single_semester_request_passes_that_scope_to_the_generator() {
    happyPath();
    var s5 = Semester.builder().id(UUID.randomUUID()).ref(SemesterRef.S5).build();
    when(semesterRepository.findByRef(SemesterRef.S5)).thenReturn(Optional.of(s5));

    subject.request(STUDENT_ID, SemesterRef.S5);

    verify(pdfGenerator).generate(any(), org.mockito.ArgumentMatchers.eq(SemesterRef.S5));
  }

  @Test
  void an_upload_failure_ends_failed_and_publishes_nothing() {
    happyPath();
    doThrow(new RuntimeException("S3 is unreachable"))
        .when(bucketComponent)
        .upload(any(), anyString());

    var result = subject.request(STUDENT_ID, null);

    assertEquals(TranscriptStatus.FAILED, result.getStatus());
    verify(eventProducer, never()).accept(any());
  }

  @Test
  void an_upload_failure_never_leaks_the_underlying_error() {
    happyPath();
    doThrow(new RuntimeException("software.amazon.awssdk NoSuchBucket: hei-prod-bucket"))
        .when(bucketComponent)
        .upload(any(), anyString());

    var result = subject.request(STUDENT_ID, null);

    assertEquals("The transcript could not be stored", result.getErrorMessage());
    assertFalse(result.getErrorMessage().contains("hei-prod-bucket"));
    assertFalse(result.getErrorMessage().contains("awssdk"));
  }

  @Test
  void a_rendering_failure_ends_failed_without_touching_the_bucket() {
    happyPath();
    when(pdfGenerator.generate(any(), any())).thenThrow(new IllegalStateException("bad template"));

    var result = subject.request(STUDENT_ID, null);

    assertEquals(TranscriptStatus.FAILED, result.getStatus());
    assertEquals("The transcript could not be generated", result.getErrorMessage());
    verify(bucketComponent, never()).upload(any(), anyString());
    verify(eventProducer, never()).accept(any());
  }

  @Test
  void a_failed_request_keeps_no_bucket_key() {
    happyPath();
    doThrow(new RuntimeException("S3 is unreachable"))
        .when(bucketComponent)
        .upload(any(), anyString());

    subject.request(STUDENT_ID, null);

    assertNull(savedState().getS3Key());
  }

  @Test
  void authorization_is_checked_before_anything_is_written() {
    doThrow(new ForbiddenException("A student may only read their own record"))
        .when(studentAuthorizer)
        .checkCanRead(STUDENT_ID);

    assertThrows(ForbiddenException.class, () -> subject.request(STUDENT_ID, null));

    verify(transcriptRequestRepository, never()).save(any());
    verify(pdfGenerator, never()).generate(any(), any());
  }

  @Test
  void an_unknown_student_is_not_found() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.request(STUDENT_ID, null));

    verify(transcriptRequestRepository, never()).save(any());
  }

  @Test
  void an_unknown_semester_is_not_found() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(semesterRepository.findByRef(SemesterRef.S5)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.request(STUDENT_ID, SemesterRef.S5));
  }

  @Test
  void an_unknown_request_is_not_found() {
    var id = UUID.randomUUID();
    when(transcriptRequestRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void reading_a_request_is_authorized_against_its_student() {
    var id = UUID.randomUUID();
    when(transcriptRequestRepository.findById(id))
        .thenReturn(Optional.of(TranscriptRequest.builder().id(id).student(STUDENT).build()));

    subject.findById(id);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }

  @Test
  void listing_a_student_s_requests_is_authorized_first() {
    when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT));
    when(transcriptRequestRepository.findAllByStudentIdOrderByRequestedAtDesc(STUDENT_ID))
        .thenReturn(List.of());

    subject.findAllByStudentId(STUDENT_ID);

    verify(studentAuthorizer).checkCanRead(STUDENT_ID);
  }
}
