package com.exam.hei.repository;

import com.exam.hei.repository.model.TranscriptRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscriptRequestRepository extends JpaRepository<TranscriptRequest, UUID> {

  /** Most recent first, which is the order the index on (student_id, requested_at desc) serves. */
  List<TranscriptRequest> findAllByStudentIdOrderByRequestedAtDesc(UUID studentId);
}
