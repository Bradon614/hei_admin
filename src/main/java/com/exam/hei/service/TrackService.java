package com.exam.hei.service;

import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class TrackService {
  private final TrackRepository trackRepository;

  public List<Track> findAll() {
    return trackRepository.findAll(Sort.by("code"));
  }

  public Track findById(UUID id) {
    return trackRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Track " + id + " not found"));
  }

  @Transactional
  public List<Track> saveAll(List<Track> tracks) {
    tracks.stream().map(Track::getId).filter(java.util.Objects::nonNull).forEach(this::findById);
    return trackRepository.saveAll(tracks);
  }
}
