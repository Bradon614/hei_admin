package com.exam.hei.model;

import com.exam.hei.repository.model.Track;

public record TrackResolution(Status status, Track track) {
  public enum Status {
    COMMON_CORE,
    RESOLVED,
    TRACK_NOT_SELECTED
  }

  public static TrackResolution commonCore() {
    return new TrackResolution(Status.COMMON_CORE, null);
  }

  public static TrackResolution resolved(Track track) {
    return new TrackResolution(Status.RESOLVED, track);
  }

  public static TrackResolution notSelected() {
    return new TrackResolution(Status.TRACK_NOT_SELECTED, null);
  }

  public boolean isResolved() {
    return status == Status.RESOLVED;
  }

  public boolean isNotSelected() {
    return status == Status.TRACK_NOT_SELECTED;
  }
}
