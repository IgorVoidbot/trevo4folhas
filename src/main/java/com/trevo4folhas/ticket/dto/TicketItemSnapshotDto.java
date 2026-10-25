package com.trevo4folhas.ticket.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Immutable terms accepted by the bettor, as stored in {@code ticket_items}. Snapshot DTOs are
 * built once at issuance and never updated afterwards.
 *
 * <p>{@code selectedOdds} crosses the JSON boundary as a decimal string, per PRD §8.4/§8.5: {@link
 * BigDecimal} is kept internally with its exact precision and scale (never double/float).
 */
public record TicketItemSnapshotDto(
    String competitionSnapshot,
    String homeTeamSnapshot,
    String awayTeamSnapshot,
    Instant matchDateSnapshot,
    String marketSnapshot,
    String selectionSnapshot,
    String selectionCodeSnapshot,
    String periodSnapshot,
    String lineSnapshot,
    String participantSnapshot,
    String ruleSnapshot,
    String providerSnapshot,
    String bookmakerSnapshot,
    String externalFixtureIdSnapshot,
    String externalMarketIdSnapshot,
    String externalSelectionKeySnapshot,
    String specifiersSnapshot,
    @JsonSerialize(using = ToStringSerializer.class) BigDecimal selectedOdds,
    long matchVersionSnapshot,
    long marketVersionSnapshot,
    long selectionVersionSnapshot) {}
