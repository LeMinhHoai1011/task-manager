package vn.smarttask.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.smarttask.common.AppException;

/**
 * F0 supplier for the authenticated actor. Reads the {@link Principal} placed
 * into the SecurityContext by {@link SessionAuthenticationFilter}.
 * Fails closed with 401 when no authenticated session is present.
 */
public final class CurrentActor {
  private CurrentActor() {}

  public static Optional<Principal> current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Principal p)) {
      return Optional.empty();
    }
    return Optional.of(p);
  }

  /** Authenticated principal or 401 INVALID_SESSION. */
  public static Principal require() {
    return current().orElseThrow(() -> AppException.invalidSession("Missing authentication"));
  }

  /** Authenticated user id or 401 INVALID_SESSION. */
  public static UUID userId() {
    return require().userId();
  }
}
