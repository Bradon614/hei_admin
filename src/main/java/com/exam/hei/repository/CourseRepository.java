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

  /**
   * Courses a student of that track follows: the ones specific to it and the ones common to all.
   *
   * <p>Not "the courses whose track is that one": a common course belongs to the programme of every
   * track, and is counted in the credits of each.
   */
  @Query("select c from Course c where c.track is null or c.track.code = :trackCode")
  Page<Course> findAllFollowedByTrack(@Param("trackCode") String trackCode, Pageable pageable);

  /** Courses of a semester that a student of that track follows. */
  @Query(
      "select c from Course c"
          + " where c.semester.id = :semesterId"
          + " and (c.track is null or c.track.id = :trackId)"
          + " order by c.ref")
  List<Course> findAllOfSemesterFollowedByTrack(
      @Param("semesterId") UUID semesterId, @Param("trackId") UUID trackId);

  /** Courses of a common core semester: every student follows all of them. */
  List<Course> findAllBySemesterIdOrderByRefAsc(UUID semesterId);
}
