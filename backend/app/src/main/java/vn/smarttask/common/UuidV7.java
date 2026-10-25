package vn.smarttask.common;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** UUIDv7 generator (RFC 9562). Time-ordered, monotonic within ms via random bits. */
public final class UuidV7 {
  private static final SecureRandom RNG = new SecureRandom();

  private UuidV7() {}

  public static UUID generate() {
    return generate(Instant.now());
  }

  public static UUID generate(Instant instant) {
    long unixMs = instant.toEpochMilli();
    byte[] rand = new byte[10];
    RNG.nextBytes(rand);
    long msb = (unixMs << 16) | 0x7000L | (((long) rand[0] & 0xFF) << 4 | ((long) rand[1] & 0xF0) >>> 4);
    // variant bits 10xx + 62 random bits
    long lsb = 0x8000000000000000L;
    for (int i = 2; i < 10; i++) {
      lsb |= ((long) rand[i] & 0xFF) << (8 * (9 - i));
    }
    // mask to 62 low bits then set variant
    lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  public static boolean isV7(UUID id) {
    return id != null && id.version() == 7;
  }
}
