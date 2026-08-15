package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks one transcript request from the moment it is recorded to the moment its email leaves.
 *
 * <p>Persisting the request rather than generating a PDF on the spot is what makes the asynchronous
 * half observable: a caller polls this row instead of holding a connection open, and a failure
 * anywhere leaves a readable trace rather than a lost request.
 */
@Entity
@Table(name = "transcript_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TranscriptRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  @ToString.Exclude
  private Student student;

  /** Null for a full S1 to S6 transcript. */
  @ManyToOne
  @JoinColumn(name = "semester_id")
  @ToString.Exclude
  private Semester semester;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private TranscriptStatus status = TranscriptStatus.PENDING;

  /** Where the PDF landed in the bucket. Filled in by the synchronous half. */
  @Column(name = "s3_key")
  private String s3Key;

  /**
   * Presigned link to the object, filled in by the asynchronous consumer when it emails the
   * student. Deliberately still null once the synchronous half is done: a link that nobody has been
   * sent yet would misreport how far the request actually got.
   */
  @Column(name = "file_url")
  private String fileUrl;

  /** The account that asked, which is not always the student themselves: an admin may too. */
  @ManyToOne(optional = false)
  @JoinColumn(name = "requested_by", nullable = false)
  @ToString.Exclude
  private AppUser requestedBy;

  /**
   * Set by the application rather than left to the database default, so the value is readable
   * without re-reading the row. Same reasoning as {@code StudentTrackChoice.decidedAt}.
   */
  @Column(name = "requested_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant requestedAt = Instant.now();

  @Column(name = "generated_at")
  private Instant generatedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  /** Filled in alongside {@code FAILED}, never carrying a raw AWS or stack trace detail. */
  @Column(name = "error_message")
  private String errorMessage;
}
