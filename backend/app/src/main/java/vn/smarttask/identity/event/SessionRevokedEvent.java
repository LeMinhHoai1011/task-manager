package vn.smarttask.identity.event;

import java.util.UUID;

/**
 * F4: published by {@link vn.smarttask.identity.service.IdentityService} whenever a
 * session stops being usable (logout, refresh rotation, login-elsewhere revoke,
 * admin revoke). Consumers (realtime registry, push-device store) close /
 * invalidate session-bound resources. Never carries tokens.
 */
public record SessionRevokedEvent(UUID sessionId, UUID userId, String reason) {}
