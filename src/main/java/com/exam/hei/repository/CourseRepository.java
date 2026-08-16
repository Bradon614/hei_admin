package com.exam.hei.repository;

import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.SemesterRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
  Optional<Course> findByRef(String ref);

  Page<Course> findAllBySemesterRef(SemesterRef semesterRef, Pageable pageable);

  @Query("select c from Course c left join c.track t where t is null or t.code = :trackCode")
  Page<Course> findAllFollowedByTrack(@Param("trackCode") String trackCode, Pageable pageable);

  @Query(
      "select c from Course c left join c.track t"
          + " where c.semester.ref = :semesterRef"
          + " and (t is null or t.code = :trackCode)")
  Page<Course> findAllOfSemesterFollowedByTrackCode(
      @Param("semesterRef") SemesterRef semesterRef,
      @Param("trackCode") String trackCode,
      Pageable pageable);

  @Query(
      "select c from Course c"
          + " where c.semester.id = :semesterId"
          + " and (c.track is null or c.track.id = :trackId)"
          + " order by c.ref")
  List<Course> findAllOfSemesterFollowedByTrack(
      @Param("semesterId") UUID semesterId, @Param("trackId") UUID trackId);

  List<Course> findAllBySemesterIdOrderByRefAsc(UUID semesterId);
}
