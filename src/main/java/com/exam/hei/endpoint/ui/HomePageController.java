package com.exam.hei.endpoint.ui;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The two landing pages a sign-in leads to. Which one is reachable is decided by SecurityConf, not
 * here: {@code /ui/admin} falls under the administrator catch-all, {@code /ui/me} is listed before
 * it as merely authenticated.
 */
@Controller
@AllArgsConstructor
public class HomePageController {

  private final CurrentUserModel currentUserModel;

  @GetMapping("/ui/admin")
  public String admin(Model model) {
    currentUserModel.addTo(model);
    return "admin";
  }

  @GetMapping("/ui/me")
  public String me(Model model) {
    currentUserModel.addTo(model);
    return "me";
  }
}
