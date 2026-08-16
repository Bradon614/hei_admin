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

  Optional<Student> findByUserId(UUID userId);

  Page<Student> findAllByPromotionId(UUID promotionId, Pageable pageable);

  @Query(
      "select s from Student s where exists ("
          + " select 1 from StudentGroupAssignment a"
          + " where a.student = s"
          + " and a.group.id = :groupId"
          + " and a.startDate <= :date"
          + " and (a.endDate is null or a.endDate >= :date))")
  Page<Student> findAllInGroupAt(
      @Param("groupId") UUID groupId, @Param("date") LocalDate date, Pageable pageable);

  @Query(
      "select s from Student s where exists ("
          + " select 1 from StudentTrackChoice c"
          + " where c.student = s and c.track.code = :trackCode"
          + " and c.fromSemester.semOrder ="
          + "   (select max(latest.fromSemester.semOrder) from StudentTrackChoice latest"
          + "    where latest.student = s))")
  Page<Student> findAllFollowingTrack(@Param("trackCode") String trackCode, Pageable pageable);

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
