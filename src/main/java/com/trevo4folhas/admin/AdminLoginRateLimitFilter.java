package com.trevo4folhas.admin;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trevo4folhas.config.ApiErrorResponder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate-limits {@code POST /admin/login} per remote address before authentication runs (PRD RNF-09).
 * Over the limit the request never reaches the authentication provider and the caller receives
 * {@code 429} with a sanitized pt-BR body and {@code Retry-After}; the window expires on its own.
 */
public class AdminLoginRateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/admin/login";

  private final AdminLoginRateLimiter rateLimiter;

  public AdminLoginRateLimitFilter(AdminLoginRateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !(LOGIN_PATH.equals(request.getRequestURI())
        && "POST".equalsIgnoreCase(request.getMethod()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<Duration> retryAfter = rateLimiter.recordAttempt(request.getRemoteAddr());
    if (retryAfter.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    Duration retry = retryAfter.get();
    // Round UP to the next second: a client must never be told to retry before the window expires.
    long retryAfterSeconds = Math.max(1L, retry.getSeconds() + (retry.getNano() > 0 ? 1L : 0L));
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    ApiErrorResponder.writeJson(
        response,
        HttpStatus.TOO_MANY_REQUESTS.value(),
        "RATE_LIMITED",
        "Muitas tentativas de login. Aguarde um instante e tente novamente.");
  }
}
