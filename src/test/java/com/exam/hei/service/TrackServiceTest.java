package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exam.hei.model.exception.NotFoundException;
import com.exam.hei.repository.TrackRepository;
import com.exam.hei.repository.model.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackServiceTest {

  private final TrackRepository trackRepository = mock(TrackRepository.class);
  private final TrackService subject = new TrackService(trackRepository);

  private static Track track(UUID id) {
    return Track.builder().id(id).code("EL").name("Software Ecosystem").build();
  }

  @Test
  void an_unknown_track_is_not_found() {
    var id = UUID.randomUUID();
    when(trackRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.findById(id));
  }

  @Test
  void a_known_track_is_returned() {
    var id = UUID.randomUUID();
    when(trackRepository.findById(id)).thenReturn(Optional.of(track(id)));

    assertEquals(id, subject.findById(id).getId());
  }

  @Test
  void a_track_without_an_id_is_created() {
    var toCreate = track(null);
    when(trackRepository.saveAll(any())).thenReturn(List.of(toCreate));

    assertEquals(1, subject.saveAll(List.of(toCreate)).size());
  }

  @Test
  void a_track_naming_an_unknown_id_is_not_found() {
    var id = UUID.randomUUID();
    when(trackRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> subject.saveAll(List.of(track(id))));
  }
}
