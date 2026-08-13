package com.exam.hei.endpoint.rest.security;

import com.exam.hei.endpoint.rest.security.model.Principal;
import com.exam.hei.repository.model.AppUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single entry point for reading the authenticated caller.
 *
 * <p>Exists so that neither controllers nor services touch {@link SecurityContextHolder} directly:
 * authorization concerns stay inside this package, and a service that needs to scope a query to its
 * caller asks here instead of reading the security context itself.
 */
@Component
public class AuthenticatedResourceProvider {

  public AppUser getAuthenticatedUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Principal principal)) {
      throw new IllegalStateException("No authenticated user in the current security context");
    }
    return principal.getUser();
  }
}
