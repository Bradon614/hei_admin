package com.exam.hei.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "course")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Course {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "ref", nullable = false, unique = true)
  private String ref;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "credits", nullable = false)
  private int credits;

  @ManyToOne(optional = false)
  @JoinColumn(name = "semester_id", nullable = false)
  @ToString.Exclude
  private Semester semester;

  @ManyToOne
  @JoinColumn(name = "track_id")
  @ToString.Exclude
  private Track track;
}
