package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.AuthenticatedResourceProvider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Feeds the navigation bar, which every signed-in page shares. Kept in one place so the pages read
 * the caller the same way, and so no controller reaches into the security context on its own.
 */
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
