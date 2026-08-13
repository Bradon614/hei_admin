package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A cohort identified by a letter: K, H, N.
 *
 * <p>A promotion holds several groups and opens one or more tracks. Promotion, track, group and
 * student are four distinct concepts.
 */
@Entity
@Table(name = "promotion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Promotion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "ref", nullable = false, unique = true)
  private String ref;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "start_year", nullable = false)
  private int startYear;

  @Column(name = "end_year", nullable = false)
  private int endYear;

  /**
   * Tracks opened by this promotion, from the first non common core semester on. A student track
   * choice is only accepted when the chosen track appears here.
   *
   * <p>Fetched eagerly: a promotion opens two tracks at most in practice, and the REST payload
   * always carries them, so lazy loading would only trade a join for a second query.
   */
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "promotion_track",
      joinColumns = @JoinColumn(name = "promotion_id"),
      inverseJoinColumns = @JoinColumn(name = "track_id"))
  @Builder.Default
  @ToString.Exclude
  private List<Track> tracks = new ArrayList<>();
}
