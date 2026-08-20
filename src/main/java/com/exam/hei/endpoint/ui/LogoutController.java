package com.exam.hei.endpoint.ui;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LogoutController {

  @PostMapping("/ui/logout")
  public String logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.cleared().toString());
    return "redirect:/ui/login";
  }
}
