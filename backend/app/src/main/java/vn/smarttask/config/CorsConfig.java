package vn.smarttask.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS is disabled by default (same-origin deployment behind Caddy/Nginx).
 * Set {@code SMARTTASK_CORS_ALLOWED_ORIGINS} (comma-separated) only when the
 * web client is hosted on a different origin than the API.
 *
 * <p>We use bearer tokens, never cookies, so credentials are not allowed and
 * the wildcard origin is intentionally not supported.
 */
@Configuration
public class CorsConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${smarttask.cors.allowed-origins:}") String origins) {
    List<String> allowed = Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    // IMPORTANT: only register a CORS configuration when an allowlist is
    // configured. Registering an empty configuration makes Spring's CorsFilter
    // reject every browser POST that carries an Origin header (same-origin
    // requests include Origin on POST), which would break login in production.
    if (!allowed.isEmpty()) {
      CorsConfiguration c = new CorsConfiguration();
      c.setAllowedOrigins(allowed);
      c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
      c.setAllowedHeaders(List.of(
          "Authorization", "Content-Type", "Accept", "If-Match", "Idempotency-Key",
          "X-Correlation-Id", "X-Client-Type", "X-Reauth-Ticket"));
      c.setExposedHeaders(List.of(
          "Content-Disposition", "ETag", "Retry-After", "X-RateLimit-Scope",
          "X-Correlation-Id"));
      c.setAllowCredentials(false);
      c.setMaxAge(3600L);
      source.registerCorsConfiguration("/api/**", c);
      source.registerCorsConfiguration("/actuator/**", c);
    }
    return source;
  }
}
