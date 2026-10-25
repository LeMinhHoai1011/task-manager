package vn.smarttask.security;

import java.util.UUID;

/**
 * F0 authenticated principal: resolved from the opaque session token by
 * {@link SessionAuthenticationFilter}, never from client headers.
 */
public record Principal(UUID userId, UUID sessionId) {}
