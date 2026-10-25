package vn.smarttask.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.smarttask.common.CorrelationIdFilter;
import vn.smarttask.common.ErrorEnvelope;
import vn.smarttask.security.SessionAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter,
      ObjectMapper objectMapper) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
          // No principal (missing/invalid token on a protected path): 401 envelope.
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          objectMapper.writeValue(response.getWriter(), ErrorEnvelope.of(
              "INVALID_SESSION", "auth.session.invalid", "SESSION_INVALID", null,
              request.getHeader(CorrelationIdFilter.HEADER)));
        }))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**", "/actuator/health",
                "/api/v1/health/**", "/error",
                "/api/v1/files/stream/**",
                // F4 realtime SSE: ticket-in-query is the credential
                // (EventSource cannot set headers); the controller consumes
                // the single-use ticket and fails closed with 401.
                "/api/v1/realtime/stream").permitAll()
            .anyRequest().authenticated());
    http.addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
