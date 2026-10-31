package com.trevo4folhas.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.trevo4folhas.config.LoginRateLimitProperties;

/**
 * Login rate limiting (PRD RNF-09): 5 attempts per minute per IP by default, {@code 429} beyond the
 * limit and automatic recovery when the window expires — never a permanent block, and never keyed
 * by account, so an attacker cannot lock an operator out. Deterministic via an injected clock; the
 * MockMvc suite covers the HTTP side.
 */
class AdminLoginRateLimiterTest {

  private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");

  private final MutableClock clock = new MutableClock(START);
  private final AdminLoginRateLimiter limiter =
      new AdminLoginRateLimiter(new LoginRateLimitProperties(), clock);

  @Test
  void allowsConfiguredAttemptsThenRejectsUntilTheWindowExpires() {
    for (int attempt = 1; attempt <= LoginRateLimitProperties.DEFAULT_MAX_ATTEMPTS; attempt++) {
      assertThat(limiter.recordAttempt("203.0.113.10")).isEmpty();
    }

    Optional<Duration> blocked = limiter.recordAttempt("203.0.113.10");
    assertThat(blocked).isPresent();
    assertThat(blocked.get()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(60));

    // Still inside the same window: stays rejected.
    assertThat(limiter.recordAttempt("203.0.113.10")).isPresent();

    // Window expires on its own: attempts are allowed again, with no manual reset.
    clock.advance(Duration.ofSeconds(61));
    assertThat(limiter.recordAttempt("203.0.113.10")).isEmpty();
  }

  @Test
  void limitsAreIndependentPerClientAddress() {
    for (int attempt = 1; attempt <= 6; attempt++) {
      limiter.recordAttempt("203.0.113.10");
    }

    assertThat(limiter.recordAttempt("203.0.113.10")).isPresent();
    assertThat(limiter.recordAttempt("198.51.100.7")).isEmpty();
  }

  @Test
  void counterResetsWhenTheWindowRollsOverEvenWithContinuousTraffic() {
    for (int attempt = 1; attempt <= 4; attempt++) {
      assertThat(limiter.recordAttempt("203.0.113.20")).isEmpty();
    }
    clock.advance(Duration.ofSeconds(30));

    assertThat(limiter.recordAttempt("203.0.113.20")).isEmpty(); // 5th of the window
    assertThat(limiter.recordAttempt("203.0.113.20")).isPresent();

    clock.advance(Duration.ofSeconds(31)); // original window fully elapsed
    assertThat(limiter.recordAttempt("203.0.113.20")).isEmpty();
  }

  @Test
  void blockedDurationIsExactUnderAControlledClock() {
    for (int attempt = 1; attempt <= LoginRateLimitProperties.DEFAULT_MAX_ATTEMPTS; attempt++) {
      assertThat(limiter.recordAttempt("203.0.113.30")).isEmpty();
    }

    clock.advance(Duration.ofMillis(1500));
    Optional<Duration> blocked = limiter.recordAttempt("203.0.113.30");
    assertThat(blocked).isPresent();
    // Exact remainder of the 60-second window after 1.5 seconds of real work.
    assertThat(blocked.get()).isEqualTo(Duration.ofMillis(58_500));
  }

  @Test
  void retryAfterHeaderRoundsUpSoAClientNeverRetriesBeforeTheWindowExpires() throws Exception {
    for (int attempt = 1; attempt <= LoginRateLimitProperties.DEFAULT_MAX_ATTEMPTS; attempt++) {
      limiter.recordAttempt("203.0.113.40");
    }

    clock.advance(Duration.ofMillis(1500)); // 58.5s left in the window
    AdminLoginRateLimitFilter filter = new AdminLoginRateLimitFilter(limiter);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/login");
    request.setRemoteAddr("203.0.113.40");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          throw new AssertionError("an over-the-limit request must not reach the chain");
        });

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("59");
  }

  /** Test-only clock whose instant moves under the test's control. */
  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
