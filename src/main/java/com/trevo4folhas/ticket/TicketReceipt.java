package com.trevo4folhas.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.trevo4folhas.ticket.dto.TicketItemSnapshotDto;

/** Private response, available only to the issuing session. Never log or cache it. */
public record TicketReceipt(
    UUID id,
    String code,
    TicketStatus status,
    Instant createdAt,
    Instant expiresAt,
    String stakeAmount,
    String totalOdds,
    String potentialReturn,
    String bettorName,
    String bettorPhone,
    String rulesVersion,
    List<TicketItemSnapshotDto> items,
    String summary,
    String whatsappMessage,
    String bankWhatsappNumber,
    Instant whatsappClickedAt) {
  @Override
  public String toString() {
    return "TicketReceipt[redacted]";
  }
}
