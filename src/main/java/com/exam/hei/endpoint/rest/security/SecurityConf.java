package com.exam.hei.endpoint.rest.security;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConf {
  private static final String[] PUBLIC_PATHS = {"/ping", "/health/**"};

  private final AuthProvider authProvider;
  private final ObjectMapper objectMapper;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    var authenticationManager = new ProviderManager(authProvider);

    var authenticationEntryPoint =
        new UiAwareAuthenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper));
    var accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);

    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            requests ->
                requests
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/login")
                    .permitAll()
                    .requestMatchers("/ui/login")
                    .permitAll()
                    .requestMatchers("/css/**")
                    .permitAll()
                    .requestMatchers("/ui/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/promotions",
                        "/tracks",
                        "/students",
                        "/teachers",
                        "/promotions/*/groups",
                        "/courses",
                        "/teaching-assignments")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/courses/*/exams", "/exams/*/grades")
                    .hasAnyRole("ADMIN", "TEACHER")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/students/*/group-assignments",
                        "/students/*/track-choices")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/students")
                    .hasAnyRole("ADMIN", "TEACHER")
                    .requestMatchers(HttpMethod.GET, "/teachers")
                    .hasAnyRole("ADMIN", "TEACHER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/promotions/*/results",
                        "/promotions/*/graduates",
                        "/promotions/*/graduates/excel")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new BearerAuthFilter(authenticationManager, authenticationEntryPoint),
            AnonymousAuthenticationFilter.class)
        .build();
  }
}
