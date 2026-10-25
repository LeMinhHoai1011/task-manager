package vn.smarttask.identity.event;

import java.util.UUID;

/**
 * F4: published when an account loses access entirely (e.g. registration
 * rejected). Consumers close every connection of the user. There is no
 * suspend endpoint in V1; locked/expired sessions are additionally reaped by
 * the realtime sweeper, so this event only covers explicit access removal.
 */
public record UserAccessRevokedEvent(UUID userId, String reason) {}
