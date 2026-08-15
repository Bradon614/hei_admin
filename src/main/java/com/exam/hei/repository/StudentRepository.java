package com.exam.hei.repository;

import com.exam.hei.repository.model.Student;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

  Optional<Student> findByRef(String ref);

  Optional<Student> findByEmail(String email);

  /** Resolves the profile behind an authenticated account. */
  Optional<Student> findByUserId(UUID userId);

  Page<Student> findAllByPromotionId(UUID promotionId, Pageable pageable);

  /**
   * Students who belonged to that group on that date, bounds inclusive.
   *
   * <p>Rooted on the student rather than on the assignment so that the caller can sort by a student
   * attribute: a sort is applied to the root of the query, and an assignment has no reference.
   */
  @Query(
      "select s from Student s where exists ("
          + " select 1 from StudentGroupAssignment a"
          + " where a.student = s"
          + " and a.group.id = :groupId"
          + " and a.startDate <= :date"
          + " and (a.endDate is null or a.endDate >= :date))")
  Page<Student> findAllInGroupAt(
      @Param("groupId") UUID groupId, @Param("date") LocalDate date, Pageable pageable);

  /**
   * Students whose most recent track choice is that track.
   *
   * <p>Compares against the latest choice rather than any choice, so that a student who reoriented
   * is counted in the track they now follow and not in both.
   */
  @Query(
      "select s from Student s where exists ("
          + " select 1 from StudentTrackChoice c"
          + " where c.student = s and c.track.code = :trackCode"
          + " and c.fromSemester.semOrder ="
          + "   (select max(latest.fromSemester.semOrder) from StudentTrackChoice latest"
          + "    where latest.student = s))")
  Page<Student> findAllFollowingTrack(@Param("trackCode") String trackCode, Pageable pageable);

  /**
   * Same rule as {@link #findAllFollowingTrack}, scoped to one promotion for the results listing.
   */
  @Query(
      "select s from Student s where s.promotion.id = :promotionId and exists ("
          + " select 1 from StudentTrackChoice c"
          + " where c.student = s and c.track.code = :trackCode"
          + " and c.fromSemester.semOrder ="
          + "   (select max(latest.fromSemester.semOrder) from StudentTrackChoice latest"
          + "    where latest.student = s))")
  Page<Student> findAllByPromotionIdFollowingTrack(
      @Param("promotionId") UUID promotionId,
      @Param("trackCode") String trackCode,
      Pageable pageable);
}
