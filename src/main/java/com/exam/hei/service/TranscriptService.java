package com.exam.hei.service;

import com.exam.hei.endpoint.event.EventProducer;
import com.exam.hei.endpoint.event.model.TranscriptGenerated;
import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import com.exam.hei.endpoint.rest.security.StudentAuthorizer;
import com.exam.hei.file.bucket.BucketComponent;
import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.SemesterRepository;
import com.exam.hei.repository.StudentRepository;
import com.exam.hei.repository.TranscriptRequestRepository;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.TranscriptRequest;
import com.exam.hei.repository.model.TranscriptStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The synchronous half of a transcript request: build the PDF, store it, mark the request
 * GENERATED, then announce it.
 *
 * <p>Where the boundary sits, and why. Everything up to and including the upload happens while the
 * caller waits, so a failure is reported as a failure rather than swallowed into a queue. The email
 * happens afterwards, driven by {@link TranscriptGenerated}, because a mail server being slow is no
 * reason to hold an HTTP connection open.
 *
 * <p>Deliberately not annotated {@code @Transactional}. Each save is its own transaction, so a
 * request that fails halfway still leaves a FAILED row behind to explain itself; a single
 * surrounding transaction would roll that explanation back along with everything else.
 */
@Service
@AllArgsConstructor
@Slf4j
public class TranscriptService {

  /**
   * What the caller is told when something breaks. Deliberately vague: an AWS error, a bucket name
   * or a stack trace would tell an outsider about the infrastructure and nothing useful about their
   * request. The real cause goes to the log.
   */
  private static final String GENERATION_FAILED = "The transcript could not be generated";

  private static final String STORAGE_FAILED = "The transcript could not be stored";

  private final TranscriptRequestRepository transcriptRequestRepository;
  private final StudentRepository studentRepository;
  private final SemesterRepository semesterRepository;
  private final ResultService resultService;
  private final TranscriptPdfGenerator pdfGenerator;
  private final BucketComponent bucketComponent;
  private final EventProducer<TranscriptGenerated> eventProducer;
  private final StudentAuthorizer studentAuthorizer;
  private final AuthenticatedResourceProvider authenticatedResourceProvider;

  /**
   * @param semesterRef the only semester to print, or null for the whole S1 to S6 curriculum
   */
  public TranscriptRequest request(UUID studentId, SemesterRef semesterRef) {
    // Checked before anything is written: an unauthorized caller must not leave a request behind.
    studentAuthorizer.checkCanRead(studentId);
    var student = requireStudent(studentId);
    var semester = semesterRef == null ? null : requireSemester(semesterRef);

    var transcriptRequest =
        transcriptRequestRepository.save(
            TranscriptRequest.builder()
                .student(student)
                .semester(semester)
                .status(TranscriptStatus.PENDING)
                .requestedBy(authenticatedResourceProvider.getAuthenticatedUser())
                .build());

    return generateAndStore(transcriptRequest, studentId, semesterRef);
  }

  private TranscriptRequest generateAndStore(
      TranscriptRequest transcriptRequest, UUID studentId, SemesterRef semesterRef) {
    byte[] pdf;
    try {
      pdf = pdfGenerator.generate(resultService.resultOf(studentId), semesterRef);
    } catch (RuntimeException e) {
      log.error("Transcript {} could not be rendered", transcriptRequest.getId(), e);
      return fail(transcriptRequest, GENERATION_FAILED);
    }

    var s3Key = s3KeyOf(transcriptRequest);
    try {
      upload(pdf, s3Key);
    } catch (RuntimeException | IOException e) {
      log.error("Transcript {} could not be uploaded to {}", transcriptRequest.getId(), s3Key, e);
      return fail(transcriptRequest, STORAGE_FAILED);
    }

    transcriptRequest.setS3Key(s3Key);
    transcriptRequest.setStatus(TranscriptStatus.GENERATED);
    transcriptRequest.setGeneratedAt(Instant.now());
    var generated = transcriptRequestRepository.save(transcriptRequest);

    // Published last, and only here: an event announcing a file that failed to upload would send
    // the consumer looking for an object that does not exist.
    eventProducer.accept(
        List.of(TranscriptGenerated.builder().transcriptRequestId(generated.getId()).build()));
    return generated;
  }

  /** {@code BucketComponent} uploads a file, so the bytes touch disk briefly and are cleaned up. */
  private void upload(byte[] pdf, String s3Key) throws IOException {
    Path temporary = null;
    try {
      temporary = Files.createTempFile("transcript", ".pdf");
      Files.write(temporary, pdf);
      bucketComponent.upload(temporary.toFile(), s3Key);
    } finally {
      if (temporary != null) {
        Files.deleteIfExists(temporary);
      }
    }
  }

  private TranscriptRequest fail(TranscriptRequest transcriptRequest, String message) {
    transcriptRequest.setStatus(TranscriptStatus.FAILED);
    transcriptRequest.setErrorMessage(message);
    return transcriptRequestRepository.save(transcriptRequest);
  }

  /** Namespaced by student so a bucket listing stays readable, keyed by request so it is unique. */
  private String s3KeyOf(TranscriptRequest transcriptRequest) {
    return "transcripts/"
        + transcriptRequest.getStudent().getId()
        + "/"
        + transcriptRequest.getId()
        + ".pdf";
  }

  public List<TranscriptRequest> findAllByStudentId(UUID studentId) {
    studentAuthorizer.checkCanRead(studentId);
    requireStudent(studentId);
    return transcriptRequestRepository.findAllByStudentIdOrderByRequestedAtDesc(studentId);
  }

  public TranscriptRequest findById(UUID id) {
    var transcriptRequest =
        transcriptRequestRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Transcript request " + id + " not found"));
    studentAuthorizer.checkCanRead(transcriptRequest.getStudent().getId());
    return transcriptRequest;
  }

  private Student requireStudent(UUID studentId) {
    return studentRepository
        .findById(studentId)
        .orElseThrow(() -> new NotFoundException("Student " + studentId + " not found"));
  }

  private Semester requireSemester(SemesterRef semesterRef) {
    return semesterRepository
        .findByRef(semesterRef)
        .orElseThrow(() -> new NotFoundException("Semester " + semesterRef + " not found"));
  }
}
