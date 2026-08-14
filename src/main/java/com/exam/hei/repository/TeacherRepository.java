package com.exam.hei.repository;

import com.exam.hei.repository.model.Teacher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

  Optional<Teacher> findByRef(String ref);

  Optional<Teacher> findByEmail(String email);

  /** Resolves the profile behind an authenticated account. */
  Optional<Teacher> findByUserId(UUID userId);
}
