package com.trevo4folhas.ticket;

import java.time.Instant;
import java.util.*;

import com.trevo4folhas.catalog.CatalogDtos.Page;

public final class TicketOperationsDtos {
  private TicketOperationsDtos() {}

  public record Row(
      UUID id,
      String code,
      String bettorName,
      String bettorPhone,
      String stakeAmount,
      String potentialReturn,
      TicketStatus status,
      Instant createdAt,
      Instant expiresAt,
      long ticketVersion,
      boolean affectedByCancelledMatch) {
    @Override
    public String toString() {
      return "TicketRow[redacted]";
    }
  }

  public record TicketQueue(
      Page<Row> tickets, Map<TicketStatus, Long> counts, String bankTimeZone) {}

  public record Detail(
      TicketReceipt ticket,
      long ticketVersion,
      UUID handledBy,
      String handledByName,
      Instant handledAt,
      String cancellationReason,
      String operatorNote,
      boolean affectedByCancelledMatch) {
    @Override
    public String toString() {
      return "TicketDetail[redacted]";
    }
  }

  public record Decision(boolean changed, String errorCode, String message, Detail current) {
    @Override
    public String toString() {
      return "TicketDecision[redacted]";
    }
  }
}
