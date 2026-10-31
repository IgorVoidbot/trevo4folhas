package com.trevo4folhas.sports;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "trevo.sports", name = "sync-enabled", havingValue = "true")
public class SyncScheduler {
  private final CatalogSyncService service;

  public SyncScheduler(
      CatalogSyncService service,
      SportsProperties properties,
      @org.springframework.beans.factory.annotation.Value(
              "${trevo.sports.sync-interval-seconds:3600}")
          long interval) {
    if (interval < Math.max(1, properties.maxRunSeconds())
        || interval > 86400
        || ((86400 + interval - 1) / interval)
                * properties.maxPages()
                * (properties.maxRetries() + 1L)
            > properties.dailyRequestBudget() - properties.requestReserve()) {
      throw new IllegalArgumentException(
          "O intervalo de sincronização excede o orçamento configurado.");
    }
    this.service = service;
  }

  @Scheduled(
      fixedDelayString = "${trevo.sports.sync-interval-seconds:3600}",
      timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  public void synchronize() {
    try {
      service.run();
    } catch (SyncFailure blocked) {
      /* Sanitized state is available in the authenticated status API. */
    }
  }
}
