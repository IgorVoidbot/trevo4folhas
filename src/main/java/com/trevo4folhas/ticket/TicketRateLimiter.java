package com.trevo4folhas.ticket;

import java.time.*;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Single-process fixed windows for the initial monolith. Keys contain hashes, not IP/PII. */
@Component
public class TicketRateLimiter {
  private record Window(Instant start, int count) {}

  private final Map<String, Window> windows = new HashMap<>();
  private final TicketRateProperties limits;
  private final Clock clock;

  public TicketRateLimiter(TicketRateProperties limits, Clock clock) {
    this.limits = limits;
    this.clock = clock;
  }

  public synchronized void check(String ip, String sessionHash) {
    Instant now = clock.instant();
    windows
        .entrySet()
        .removeIf(
            entry -> !now.isBefore(entry.getValue().start().plusSeconds(limits.windowSeconds())));
    if (windows.size() >= 10000) reject(limits.windowSeconds());
    long wait = 0;
    for (String key : new String[] {"ip:" + TicketIntent.hash(ip), "session:" + sessionHash}) {
      Window previous = windows.get(key);
      Window updated =
          previous == null
              ? new Window(now, 1)
              : new Window(previous.start(), previous.count() + 1);
      windows.put(key, updated);
      if (updated.count() > limits.maxAttempts())
        wait =
            Math.max(
                wait,
                Math.max(
                    1,
                    Duration.between(now, updated.start().plusSeconds(limits.windowSeconds()))
                            .toSeconds()
                        + 1));
    }
    if (wait > 0) reject(wait);
  }

  private void reject(long seconds) {
    throw new TicketException(
        HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMITED",
        "Muitas solicitações. Aguarde antes de tentar novamente; seu formulário foi preservado.",
        seconds);
  }
}
