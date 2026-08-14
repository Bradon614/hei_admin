package com.exam.hei.repository;

import com.exam.hei.repository.model.Group;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

  List<Group> findAllByPromotionIdOrderByRefAsc(UUID promotionId);

  Optional<Group> findByPromotionIdAndRef(UUID promotionId, String ref);
}
