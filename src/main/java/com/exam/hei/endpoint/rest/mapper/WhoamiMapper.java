package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Whoami;
import com.exam.hei.repository.model.AppUser;
import org.springframework.stereotype.Component;

@Component
public class WhoamiMapper {

  public Whoami toRest(AppUser user) {
    // studentId and teacherId are left null: the student and teacher profiles are introduced by
    // their own features, and nothing links an account to one of them yet.
    return Whoami.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .build();
  }
}
