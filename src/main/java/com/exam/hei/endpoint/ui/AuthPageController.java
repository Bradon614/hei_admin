package com.exam.hei.endpoint.ui;

import com.exam.hei.endpoint.rest.security.BearerAuthFilter;
import com.exam.hei.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Browser sign-in for the Thymeleaf pages.
 *
 * <p>A browser cannot attach an {@code Authorization} header to a plain navigation, so the token it
 * gets here travels in a cookie instead. That cookie is the only reason {@link BearerAuthFilter}
 * looks anywhere other than the header.
 */
@Controller
@AllArgsConstructor
public class AuthPageController {

  private final AuthService authService;

  @GetMapping("/ui/login")
  public String loginForm() {
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
          HttpHeaders.SET_COOKIE, cookieFor(issued.token(), issued.expiresInSeconds()).toString());
      return "redirect:/ui/graduates";
    } catch (AuthenticationException e) {
      // Same message the API returns, for the same reason: never reveal which emails exist.
      model.addAttribute("error", e.getMessage());
      return "login";
    }
  }

  /**
   * {@code SameSite=Strict} is what keeps CSRF closed: a browser never sends this cookie on a
   * request another site initiated, which is why {@code SecurityConf} can leave CSRF protection
   * off. {@code HttpOnly} keeps the token out of reach of any script, so an XSS cannot steal it.
   */
  private ResponseCookie cookieFor(String token, long maxAgeSeconds) {
    return ResponseCookie.from(BearerAuthFilter.COOKIE_NAME, token)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(Duration.ofSeconds(maxAgeSeconds))
        .build();
  }
}
