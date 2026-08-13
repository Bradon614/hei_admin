package com.exam.hei.repository;

import com.exam.hei.repository.model.Promotion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

  Optional<Promotion> findByRef(String ref);
}
