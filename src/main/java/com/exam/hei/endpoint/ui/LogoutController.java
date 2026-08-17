package com.exam.hei.endpoint.ui;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Signing out has to happen server side: the session cookie is {@code HttpOnly}, so no script on
 * the page can clear it.
 *
 * <p>A POST rather than a GET. A {@code GET /ui/logout} would be triggerable by an image tag on any
 * other site, which is a nuisance rather than a breach, but the fix costs a form.
 */
@Controller
public class LogoutController {

  @PostMapping("/ui/logout")
  public String logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.cleared().toString());
    return "redirect:/ui/login";
  }
}
