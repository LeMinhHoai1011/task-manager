package vn.smarttask.task;

import vn.smarttask.common.AppException;

/** Accepts legacy numeric versions and quoted entity tags consistently. */
public record TaskVersion(long value) {
  public static TaskVersion valueOf(String header) {
    if (header == null || header.isBlank()) throw AppException.ifMatchRequired();
    String value = header.trim();
    if (value.startsWith("W/")) value = value.substring(2);
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
      value = value.substring(1, value.length() - 1);
    try {
      long version = Long.parseLong(value);
      if (version < 1) throw new NumberFormatException();
      return new TaskVersion(version);
    } catch (NumberFormatException e) { throw AppException.badRequest("IF_MATCH_INVALID", "Invalid If-Match"); }
  }
}
