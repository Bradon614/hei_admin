package com.exam.hei.endpoint.rest.mapper;

import com.exam.hei.endpoint.rest.model.Whoami;
import com.exam.hei.repository.model.AppUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WhoamiMapper {

  /**
   * @param studentId null unless the caller owns a student record
   * @param teacherId null unless the caller owns a teacher record
   */
  public Whoami toRest(AppUser user, UUID studentId, UUID teacherId) {
    return Whoami.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .role(user.getRole())
        .studentId(studentId)
        .teacherId(teacherId)
        .build();
  }
}
