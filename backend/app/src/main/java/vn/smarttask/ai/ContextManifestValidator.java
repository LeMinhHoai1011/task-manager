package vn.smarttask.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure manifest guard (no Spring / JPA imports — ArchUnit-safe).
 * Rejects forbidden fields and out-of-scope access with 403 AI_CONTEXT_DENIED.
 */
public final class ContextManifestValidator {
  private ContextManifestValidator() {}

  public record ManifestData(boolean enabled, List<String> allowedFields,
      List<String> forbiddenPatterns) {}

  public record ValidationResult(List<String> effectiveFields) {}

  public static ValidationResult validate(ManifestData manifest, List<String> requestedFields,
      UUID projectId, UUID userId, ProjectApi projectApi) {
    if (manifest == null || !manifest.enabled()) {
      throw AiErrors.contextDenied("AI context denied: manifest disabled");
    }
    List<String> requested = requestedFields == null ? List.of() : requestedFields;
    List<String> allowed = manifest.allowedFields() == null ? List.of() : manifest.allowedFields();
    List<String> forbidden = manifest.forbiddenPatterns() == null ? List.of() : manifest.forbiddenPatterns();
    List<String> effective = new ArrayList<>();
    for (String field : requested) {
      String f = field == null ? "" : field.trim();
      if (f.isEmpty()) continue;
      if (!allowed.contains(f) || matchesForbidden(f, forbidden)) {
        throw AiErrors.contextDenied("Forbidden AI context field: " + f);
      }
      effective.add(f);
    }
    // Scope check: project-scoped features require participation (2nd-factor is reauth at service layer).
    if (projectId != null) {
      if (projectApi == null || !projectApi.hasParticipation(projectId, userId)) {
        throw AiErrors.contextDenied("AI context scope outside permission");
      }
    }
    return new ValidationResult(List.copyOf(effective));
  }

  static boolean matchesForbidden(String field, List<String> patterns) {
    String lower = field.toLowerCase();
    for (String p : patterns) {
      if (p == null) continue;
      String pat = p.trim().toLowerCase();
      if (pat.isEmpty()) continue;
      // Minimal glob: '*' matches any substring (manual, no extra deps).
      if (pat.contains("*")) {
        String[] parts = pat.split("\\*", -1);
        int idx = 0;
        boolean ok = true;
        for (String part : parts) {
          if (part.isEmpty()) continue;
          int found = lower.indexOf(part, idx);
          if (found < 0) { ok = false; break; }
          idx = found + part.length();
        }
        if (ok) return true;
      } else if (lower.equals(pat) || lower.contains(pat)) {
        return true;
      }
    }
    return false;
  }
}
