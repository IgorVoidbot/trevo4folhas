package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.*;

import java.time.*;

import org.junit.jupiter.api.Test;

class TicketRateLimiterTest {
  @Test
  void limitsIpAndSessionAndRecoversAfterWindowWithoutTrustingForwardedIp() {
    Instant now = Instant.parse("2026-10-31T12:00:00Z");
    TicketRateLimiter limiter =
        new TicketRateLimiter(new TicketRateProperties(1, 60), Clock.fixed(now, ZoneOffset.UTC));
    limiter.check("address-a", "session-a");
    assertThatThrownBy(() -> limiter.check("address-a", "session-b"))
        .isInstanceOf(TicketException.class);
    assertThatThrownBy(() -> limiter.check("address-b", "session-a"))
        .isInstanceOf(TicketException.class);
  }

  @Test
  void expiredWindowsPermitNewAttempts() {
    var instant = new java.util.concurrent.atomic.AtomicReference<>(Instant.now());
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId zone) {
            return this;
          }

          public Instant instant() {
            return instant.get();
          }
        };
    TicketRateLimiter limiter = new TicketRateLimiter(new TicketRateProperties(1, 60), clock);
    limiter.check("a", "s");
    assertThatThrownBy(() -> limiter.check("a", "s")).isInstanceOf(TicketException.class);
    instant.set(instant.get().plusSeconds(60));
    limiter.check("a", "s");
  }
}
