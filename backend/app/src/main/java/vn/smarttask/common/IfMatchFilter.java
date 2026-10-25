package vn.smarttask.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stub: optimistic-concurrency guard via If-Match header.
 * Real check (compare with row version/etag) is done in service layer;
 * this filter only ensures the header is visible downstream.
 */
@Component
public class IfMatchFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    // No-op stub: services read If-Match via @RequestHeader. Keep filter for pipeline ordering.
    chain.doFilter(request, response);
  }
}
