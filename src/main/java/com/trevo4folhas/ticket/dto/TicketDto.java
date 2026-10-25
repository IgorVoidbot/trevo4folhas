package com.trevo4folhas.ticket.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.trevo4folhas.ticket.Ticket;
import com.trevo4folhas.ticket.TicketStatus;

/**
 * Public-safe ticket summary: no bettor name, phone, session or request hashes, no operator data.
 * Responses carrying it must set {@code Cache-Control: no-store} at the HTTP layer.
 *
 * <p>Financial decimals ({@code stakeAmount}, {@code totalOdds}, {@code potentialReturn}) cross the
 * JSON boundary as decimal strings, per PRD §8.4/§8.5: {@link BigDecimal} is kept internally with
 * its exact precision and scale (never double/float) and serialized with its exact textual value.
 */
public record TicketDto(
    UUID id,
    String code,
    TicketStatus status,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal stakeAmount,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal totalOdds,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal potentialReturn,
    Instant expiresAt,
    List<TicketItemSnapshotDto> items) {

  public static TicketDto from(Ticket ticket, List<TicketItemSnapshotDto> items) {
    return new TicketDto(
        ticket.getId(),
        ticket.getCode(),
        ticket.getStatus(),
        ticket.getStakeAmount(),
        ticket.getTotalOdds(),
        ticket.getPotentialReturn(),
        ticket.getExpiresAt(),
        List.copyOf(items));
  }
}
