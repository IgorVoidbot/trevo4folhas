package com.trevo4folhas.ticket;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
    prefix = "trevo.tickets.expiration",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TicketExpirationScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(TicketExpirationScheduler.class);
  private final TicketExpirationService service;

  public TicketExpirationScheduler(TicketExpirationService service) {
    this.service = service;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void expire() {
    try {
      for (int batch = 0; batch < 20; batch++) if (service.expireBatch() < 200) break;
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException failure) {
      LOG.warn("event=ticket_expiration_failed errorCode=STORAGE_UNAVAILABLE");
    }
  }
}
