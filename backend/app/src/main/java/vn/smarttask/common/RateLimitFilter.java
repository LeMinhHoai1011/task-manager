package vn.smarttask.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PH-7 rate-limit filter: bucket by path (§15 groups), 429 carries Retry-After +
 * X-RateLimit-Scope only — never the threshold/window/counter.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {
  private final RateLimiter limiter = new RateLimiter();

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    RateLimiter.Bucket bucket = bucketFor(request.getRequestURI());
    String key = clientKey(request);
    var verdict = limiter.check(bucket, key);
    if (!verdict.allowed()) {
      response.setStatus(429);
      response.setHeader("Retry-After", String.valueOf(verdict.retryAfterSeconds()));
      response.setHeader("X-RateLimit-Scope", verdict.bucket().name());
      response.setContentType("application/json");
      String cid = response.getHeader(CorrelationIdFilter.HEADER);
      response.getWriter().write("{\"error\":{\"code\":\"RATE_LIMITED\","
          + "\"message_key\":\"error.rate_limited\","
          + "\"reason_code\":\"RATE_LIMITED\","
          + "\"details\":{},"
          + "\"retry_after_seconds\":" + verdict.retryAfterSeconds() + "},"
          + "\"correlation_id\":\"" + cid + "\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  static RateLimiter.Bucket bucketFor(String uri) {
    if (uri == null) return RateLimiter.Bucket.DEFAULT;
    if (uri.startsWith("/api/v1/auth")) return RateLimiter.Bucket.AUTH;
    if (uri.contains("recovery") || uri.contains("password")) return RateLimiter.Bucket.RECOVERY;
    if (uri.startsWith("/api/v1/files")) return RateLimiter.Bucket.FILE;
    if (uri.contains("/ai/")) return RateLimiter.Bucket.AI;
    if (uri.contains("report") || uri.contains("export") || uri.contains("dashboard")) {
      return RateLimiter.Bucket.REPORT;
    }
    return RateLimiter.Bucket.DEFAULT;
  }

  private static String clientKey(HttpServletRequest request) {
    String fwd = request.getHeader("X-Forwarded-For");
    if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
    String ip = request.getRemoteAddr();
    return ip == null ? "unknown" : ip;
  }
}
