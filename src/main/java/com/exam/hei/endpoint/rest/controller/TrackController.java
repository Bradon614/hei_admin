package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.TrackMapper;
import com.exam.hei.endpoint.rest.model.Track;
import com.exam.hei.service.TrackService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class TrackController {
  private final TrackService trackService;
  private final TrackMapper trackMapper;

  @GetMapping("/tracks")
  public List<Track> getTracks() {
    return trackService.findAll().stream().map(trackMapper::toRest).toList();
  }

  @PutMapping("/tracks")
  public List<Track> crupdateTracks(@RequestBody List<Track> tracks) {
    var saved = trackService.saveAll(tracks.stream().map(trackMapper::toDomain).toList());
    return saved.stream().map(trackMapper::toRest).toList();
  }
}
