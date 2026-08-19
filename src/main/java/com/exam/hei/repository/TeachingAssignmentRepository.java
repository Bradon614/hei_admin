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

  boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId);

  boolean existsByTeacherIdAndGroupId(UUID teacherId, UUID groupId);

  List<TeachingAssignment> findAllByCourseIdAndTeacherId(UUID courseId, UUID teacherId);
}
