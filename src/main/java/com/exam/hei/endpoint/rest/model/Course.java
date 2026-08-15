package com.exam.hei.endpoint.rest.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Course {

  private UUID id;
  private String ref;
  private String title;

  /** Credits earned when the course is validated. */
  private Integer credits;

  /** On writes, the semester may be named by its reference rather than by its id. */
  private Semester semester;

  /**
   * Null for a course everyone follows: the whole common core, and the courses shared by both
   * tracks afterwards. On writes, only the id is read.
   */
  private Track track;
}
