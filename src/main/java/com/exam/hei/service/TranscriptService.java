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

@Service
@AllArgsConstructor
@Slf4j
public class TranscriptService {
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

  public TranscriptRequest request(UUID studentId, SemesterRef semesterRef) {
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

    eventProducer.accept(
        List.of(TranscriptGenerated.builder().transcriptRequestId(generated.getId()).build()));
    return generated;
  }

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
