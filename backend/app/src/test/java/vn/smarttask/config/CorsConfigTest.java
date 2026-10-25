package vn.smarttask.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Regression guard: with no CORS allowlist (same-origin deployment) the
 * CorsFilter must not see a configuration at all. An empty CorsConfiguration
 * rejects same-origin browser POSTs because they carry an Origin header.
 */
class CorsConfigTest {

  @Test
  void noAllowlistMeansNoCorsConfiguration() {
    var source = new CorsConfig().corsConfigurationSource("");
    var req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    assertNull(source.getCorsConfiguration(req));
  }

  @Test
  void explicitAllowlistIsApplied() {
    var source = new CorsConfig()
        .corsConfigurationSource("https://app.example.com, https://admin.example.com");
    var cfg = source.getCorsConfiguration(
        new MockHttpServletRequest("GET", "/api/v1/workspaces"));
    assertNotNull(cfg);
    assertEquals(List.of("https://app.example.com", "https://admin.example.com"),
        cfg.getAllowedOrigins());
    assertEquals(Boolean.FALSE, cfg.getAllowCredentials());
  }
}
