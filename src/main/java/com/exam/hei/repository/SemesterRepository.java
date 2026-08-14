package com.exam.hei.repository;

import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, UUID> {

  Optional<Semester> findByRef(SemesterRef ref);

  List<Semester> findAllByOrderBySemOrderAsc();

  /**
   * The semester a track must be chosen at, read from the data rather than written as a constant.
   */
  Optional<Semester> findFirstByCommonCoreFalseOrderBySemOrderAsc();
}
