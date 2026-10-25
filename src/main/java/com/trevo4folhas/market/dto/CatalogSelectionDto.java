package com.trevo4folhas.market.dto;

import java.util.UUID;

import com.trevo4folhas.market.Market;
import com.trevo4folhas.market.MarketSelection;

/**
 * One selectable catalog entry, resolved server-side as described in PRD §8.3: the three identities
 * plus the accepted versions ({@code matchVersion}/{@code marketVersion}/{@code selectionVersion}),
 * the semantic identity components and the odds as a decimal string. Relations stay on the server.
 */
public record CatalogSelectionDto(
    UUID matchId,
    UUID marketId,
    UUID selectionId,
    long matchVersion,
    long marketVersion,
    long selectionVersion,
    String marketType,
    String selectionCode,
    String marketDisplayName,
    String selectionDisplayName,
    String period,
    String line,
    String participant,
    String rule,
    String odds) {

  public static CatalogSelectionDto from(MarketSelection selection) {
    Market market = selection.getMarket();
    return new CatalogSelectionDto(
        market.getMatch().getId(),
        market.getId(),
        selection.getId(),
        market.getMatch().getVersion(),
        market.getVersion(),
        selection.getVersion(),
        market.getMarketType(),
        selection.getSelectionCode(),
        market.getDisplayName(),
        selection.getDisplayName(),
        market.getPeriod(),
        market.getLine(),
        market.getParticipant(),
        market.getRuleText(),
        selection.getOdds().toPlainString());
  }
}
