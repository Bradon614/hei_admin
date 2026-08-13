package com.exam.hei.repository;

import com.exam.hei.repository.model.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

  Optional<Student> findByRef(String ref);

  Optional<Student> findByEmail(String email);

  /** Resolves the profile behind an authenticated account. */
  Optional<Student> findByUserId(UUID userId);

  Page<Student> findAllByPromotionId(UUID promotionId, Pageable pageable);
}
