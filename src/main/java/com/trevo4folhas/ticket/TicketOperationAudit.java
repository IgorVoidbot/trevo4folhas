package com.trevo4folhas.ticket;

import java.util.UUID;

import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class TicketOperationAudit {
  private static final Logger LOG = LoggerFactory.getLogger(TicketOperationAudit.class);

  public record Event(
      UUID ticketId, UUID actorId, String action, TicketStatus status, long version) {}

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(Event event) {
    LOG.info(
        "event=ticket_operation ticketId={} actorId={} action={} status={} version={}",
        event.ticketId(),
        event.actorId(),
        event.action(),
        event.status(),
        event.version());
  }
}
