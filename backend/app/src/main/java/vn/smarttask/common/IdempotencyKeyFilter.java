package vn.smarttask.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stub: idempotency-key handling for POST create endpoints.
 * Real store is idempotency_record table (see V1 migration); this
 * filter only forwards. Service layer owns replay semantics.
 */
@Component
public class IdempotencyKeyFilter extends OncePerRequestFilter {
  public static final String HEADER = "Idempotency-Key";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    chain.doFilter(request, response);
  }
}
