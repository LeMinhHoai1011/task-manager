package vn.smarttask.files;

/**
 * PH-3 pure per-request file gateway decision + access-event outcome mapping.
 * Preview/download gateway re-checks auth on EVERY request; a fresh request after
 * revoke yields 404 (not 403) so existence is not leaked.
 */
public final class FileAccessDecider {
  private FileAccessDecider() {}

  public enum Decision { ALLOW, NOT_FOUND, TICKET_REJECTED }

  /** Fresh preview/download request: no READ -> NOT_FOUND (hide existence). */
  public static Decision decideFreshRequest(boolean attachmentExists, boolean notDeleted,
      boolean actorHasRead) {
    if (!attachmentExists || !notDeleted) return Decision.NOT_FOUND;
    if (!actorHasRead) return Decision.NOT_FOUND;
    return Decision.ALLOW;
  }

  public static String accessEventOutcome(Decision d) {
    return d == Decision.ALLOW ? "ALLOWED" : "DENIED";
  }

  public static Decision fromTicket(StreamTicketManager.ConsumeResult r) {
    return switch (r) {
      case OK -> Decision.ALLOW;
      case REVOKED -> Decision.NOT_FOUND;
      case EXPIRED_OR_USED -> Decision.TICKET_REJECTED;
    };
  }
}
