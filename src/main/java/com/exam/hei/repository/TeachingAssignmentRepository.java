package com.exam.hei.repository;

import com.exam.hei.repository.model.TeachingAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, UUID> {

  List<TeachingAssignment> findAllByTeacherId(UUID teacherId);

  boolean existsByCourseIdAndTeacherIdAndGroupId(UUID courseId, UUID teacherId, UUID groupId);

  /**
   * Source of truth for {@code TeacherAuthorizer}: is this teacher assigned to that course, in any
   * group.
   */
  boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId);
}
