package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.WhoamiMapper;
import com.exam.hei.endpoint.rest.model.Whoami;
import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class WhoamiController {
  private final AuthenticatedResourceProvider authenticatedResourceProvider;
  private final WhoamiMapper whoamiMapper;

  @GetMapping("/whoami")
  public Whoami whoami() {
    return whoamiMapper.toRest(
        authenticatedResourceProvider.getAuthenticatedUser(),
        authenticatedResourceProvider.getAuthenticatedStudentId().orElse(null),
        authenticatedResourceProvider.getAuthenticatedTeacherId().orElse(null));
  }
}
