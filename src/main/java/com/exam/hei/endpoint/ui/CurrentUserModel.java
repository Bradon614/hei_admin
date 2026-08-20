package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
@AllArgsConstructor
public class CurrentUserModel {

  private final AuthenticatedResourceProvider authenticatedResourceProvider;

  public void addTo(Model model) {
    var user = authenticatedResourceProvider.getAuthenticatedUser();
    model.addAttribute("currentEmail", user.getEmail());
    model.addAttribute("currentRole", user.getRole().name());
  }
}
