package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Teacher;
import org.springframework.stereotype.Component;

@Component
public class TeacherMapper {
  public Teacher toRest(com.exam.hei.repository.model.Teacher domain) {
    return Teacher.builder()
        .id(domain.getId())
        .ref(domain.getRef())
        .firstName(domain.getFirstName())
        .lastName(domain.getLastName())
        .email(domain.getEmail())
        .build();
  }

  public com.exam.hei.repository.model.Teacher toDomain(Teacher rest) {
    return com.exam.hei.repository.model.Teacher.builder()
        .id(rest.getId())
        .ref(rest.getRef())
        .firstName(rest.getFirstName())
        .lastName(rest.getLastName())
        .email(rest.getEmail())
        .password(rest.getPassword())
        .build();
  }
}
