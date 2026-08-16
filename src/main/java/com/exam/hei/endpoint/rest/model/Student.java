package com.exam.hei.endpoint.rest.model;

import com.exam.hei.repository.model.StudentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
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
public class Student {
  private UUID id;

  private String ref;

  private String firstName;
  private String lastName;
  private String email;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @ToString.Exclude
  private String password;

  private LocalDate birthDate;
  private LocalDate entranceDate;
  private StudentStatus status;

  private Promotion promotion;

  private Track currentTrack;

  private Group currentGroup;
}
