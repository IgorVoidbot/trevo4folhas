package com.trevo4folhas.ticket;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketExpirationService {
  private final TicketOperationsRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public TicketExpirationService(
      TicketOperationsRepository repository, Clock clock, ApplicationEventPublisher events) {
    this.repository = repository;
    this.clock = clock;
    this.events = events;
  }

  @Transactional(timeout = 5)
  public int expireBatch() {
    repository.beginWrite();
    var ids = repository.expireBatch(clock.instant());
    for (var expired : ids)
      events.publishEvent(
          new TicketOperationAudit.Event(
              expired.id(), null, "EXPIRE", TicketStatus.EXPIRED, expired.version()));
    return ids.size();
  }
}
