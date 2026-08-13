package com.exam.hei.endpoint.rest.security;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Closes the API by default and opens only what has to stay reachable.
 *
 * <p>All authorization rules live in this package. Controllers never carry permission logic: they
 * handle HTTP and delegate to the service layer, which reads its caller through {@link
 * AuthenticatedResourceProvider}.
 *
 * <p>Per role rules are added by the features that introduce the endpoints they protect, and are
 * consolidated later; this configuration only establishes authentication.
 */
@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConf {

  /**
   * Endpoints that must stay public.
   *
   * <p>This is not a convenience: the POJA delivery pipeline probes them with {@code curl --fail}
   * through .shell/checkHealth.sh. Requiring a token here would fail every deployment.
   */
  private static final String[] PUBLIC_PATHS = {"/ping", "/health/**"};

  private final AuthProvider authProvider;
  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    var authenticationManager = new ProviderManager(authProvider);
    var authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);

    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // Stateless: the API runs behind Lambda, there is no session to carry.
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            requests ->
                requests
                    // When a handler throws, Spring re-dispatches to /error. That dispatch must not
                    // be authenticated again: the original request was already authorized, and
                    // securing it would surface every 500 as a misleading 401.
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new BearerAuthFilter(authenticationManager, authenticationEntryPoint),
            AnonymousAuthenticationFilter.class)
        .build();
  }
}
