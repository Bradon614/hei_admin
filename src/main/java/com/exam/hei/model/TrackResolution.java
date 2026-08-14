package com.exam.hei.model;

import com.exam.hei.repository.model.Track;

/**
 * Which track applies to a student for a given semester.
 *
 * <p>Three states rather than a nullable track, because doc/api.yml insists the first two must
 * never be confused:
 *
 * <ul>
 *   <li>{@code COMMON_CORE}: S1 to S3, no track applies and that is the normal state;
 *   <li>{@code RESOLVED}: a choice covers the semester;
 *   <li>{@code TRACK_NOT_SELECTED}: the semester expects a track and no choice covers it. Not a
 *       neutral absence but a business error, so that a student who never chose is never mistaken
 *       for one who simply failed.
 * </ul>
 */
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
