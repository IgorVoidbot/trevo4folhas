package com.trevo4folhas.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.trevo4folhas.config.LoginRateLimitProperties;

/**
 * Fixed-window counter for admin login attempts per client address (PRD RNF-09: 5 attempts per
 * minute per IP by default, configurable). Excess returns {@code 429} and the window expires on its
 * own: there is no permanent lockout, and account identifiers are deliberately not part of the key,
 * so an attacker can never lock an operator out of their account. Only the servlet container's
 * remote address is trusted; forwarded headers require a known proxy configuration.
 */
@Component
public class AdminLoginRateLimiter {

  private final int maxAttempts;
  private final Duration window;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public AdminLoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
    this.maxAttempts = properties.getMaxAttempts();
    this.window = Duration.ofSeconds(properties.getWindowSeconds());
    this.clock = clock;
  }

  /**
   * Records one login attempt for {@code clientKey}. Returns empty when the attempt is allowed, or
   * the remaining time until the window expires when the caller must be rejected with {@code 429}.
   */
  public Optional<Duration> recordAttempt(String clientKey) {
    dropExpiredWindows();
    Instant now = clock.instant();
    Window updated =
        windows.compute(
            clientKey,
            (key, current) -> {
              if (current == null || !current.isOpenAt(now, window)) {
                return new Window(now, 1);
              }
              return new Window(current.startedAt, current.attempts + 1);
            });
    if (updated.attempts <= maxAttempts) {
      return Optional.empty();
    }
    Duration retryAfter = Duration.between(now, updated.startedAt.plus(window));
    return Optional.of(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
  }

  private void dropExpiredWindows() {
    Instant now = clock.instant();
    windows.entrySet().removeIf(entry -> !entry.getValue().isOpenAt(now, window));
  }

  private static final class Window {

    private final Instant startedAt;
    private final int attempts;

    private Window(Instant startedAt, int attempts) {
      this.startedAt = startedAt;
      this.attempts = attempts;
    }

    private boolean isOpenAt(Instant instant, Duration window) {
      return instant.isBefore(startedAt.plus(window));
    }
  }
}
