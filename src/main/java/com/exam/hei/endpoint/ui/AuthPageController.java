package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.JwtService;
import com.exam.hei.repository.model.Role;
import com.exam.hei.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@AllArgsConstructor
public class AuthPageController {

  private final AuthService authService;
  private final JwtService jwtService;

  @GetMapping("/ui/login")
  public String loginForm(
      @RequestParam(name = UiFeedback.PARAM, required = false) String done, Model model) {
    UiFeedback.addTo(model, done);
    return "login";
  }

  @PostMapping("/ui/login")
  public String login(
      @RequestParam String email,
      @RequestParam String password,
      Model model,
      HttpServletResponse response) {
    try {
      var issued = authService.login(email, password);
      response.addHeader(
          HttpHeaders.SET_COOKIE,
          SessionCookies.issued(issued.token(), issued.expiresInSeconds()).toString());
      return landingFor(issued.token());
    } catch (AuthenticationException e) {
      model.addAttribute("error", e.getMessage());
      return "login";
    }
  }

  private String landingFor(String token) {
    var role = jwtService.parse(token).getUser().getRole();
    return role == Role.ADMIN ? "redirect:/ui/admin" : "redirect:/ui/me";
  }
}
