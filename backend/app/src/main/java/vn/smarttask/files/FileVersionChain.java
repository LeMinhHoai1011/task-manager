package vn.smarttask.files;

import java.util.List;

/**
 * PH-3 pure version monotonicity: new version = max(existing)+1; restores create a NEW
 * version (never overwrite); restore-as-new-version copies the source object key.
 * No infrastructure imports (ArchUnit).
 */
public final class FileVersionChain {
  private FileVersionChain() {}

  public static int nextVersion(List<Integer> existingVersions) {
    int max = existingVersions.stream().mapToInt(Integer::intValue).max().orElse(0);
    return max + 1;
  }

  public static void requireMonotonic(List<Integer> existingVersions, int candidate) {
    int max = existingVersions.stream().mapToInt(Integer::intValue).max().orElse(0);
    if (candidate <= max) {
      throw new IllegalStateException("VERSION_NOT_MONOTONIC: candidate=" + candidate + " max=" + max);
    }
  }

  /** Restore always yields a fresh version number; source version id is input, output is next. */
  public static int restoreAsNewVersion(List<Integer> existingVersions) {
    return nextVersion(existingVersions);
  }
}
