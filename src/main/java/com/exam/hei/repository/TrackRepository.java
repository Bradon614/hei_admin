package com.exam.hei.repository;

import com.exam.hei.repository.model.Track;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrackRepository extends JpaRepository<Track, UUID> {
  Optional<Track> findByCode(String code);
}
