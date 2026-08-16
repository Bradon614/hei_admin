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

  @ManyToOne
  @JoinColumn(name = "semester_id")
  @ToString.Exclude
  private Semester semester;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private TranscriptStatus status = TranscriptStatus.PENDING;

  @Column(name = "s3_key")
  private String s3Key;

  @Column(name = "file_url")
  private String fileUrl;

  @ManyToOne(optional = false)
  @JoinColumn(name = "requested_by", nullable = false)
  @ToString.Exclude
  private AppUser requestedBy;

  @Column(name = "requested_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant requestedAt = Instant.now();

  @Column(name = "generated_at")
  private Instant generatedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "error_message")
  private String errorMessage;
}
