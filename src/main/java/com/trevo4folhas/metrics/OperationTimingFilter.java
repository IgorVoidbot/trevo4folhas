package com.trevo4folhas.metrics;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OperationTimingFilter extends OncePerRequestFilter {
  private final MeterRegistry registry;

  public OperationTimingFilter(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var sample = Timer.start(registry);
    boolean failed = false;
    try {
      chain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException failure) {
      failed = true;
      throw failure;
    } finally {
      int status = failed ? 500 : response.getStatus();
      String result = status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
      sample.stop(
          Timer.builder("trevo.http.duration")
              .tag("operation", operation(request))
              .tag("result", result)
              .register(registry));
    }
  }

  private String operation(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.equals("/api/tickets") && request.getMethod().equals("POST")) return "issue";
    if (path.startsWith("/api/admin/tickets/") && path.endsWith("/confirm")) return "confirm";
    if (path.startsWith("/api/admin/tickets/") && path.endsWith("/cancel")) return "cancel";
    if (path.equals("/api/admin/metrics")) return "metrics";
    if (path.equals("/api/matches")) return "catalog";
    if (path.startsWith("/api/matches/")) return "detail";
    return "other";
  }
}
