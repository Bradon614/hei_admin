package com.exam.hei.endpoint.rest.security.model;

import com.exam.hei.repository.model.AppUser;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The authenticated caller, wrapping the account resolved from the bearer API key. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Principal implements UserDetails {

  private final AppUser user;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // Spring Security expects the ROLE_ prefix for hasRole() checks.
    return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
  }

  /** Never exposed: authentication goes through the API key, not through a password. */
  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return user.getEmail();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
