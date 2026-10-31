package com.trevo4folhas.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.trevo4folhas.match.MatchStatus;

public final class CatalogDtos {
  private CatalogDtos() {}

  public record Page<T>(List<T> content, int page, int size, long totalElements, long totalPages) {
    public static <T> Page<T> of(List<T> content, int page, int size, long total) {
      return new Page<>(content, page, size, total, (total + size - 1) / size);
    }
  }

  public record MatchView(
      UUID id,
      long matchVersion,
      String competition,
      String homeTeam,
      String awayTeam,
      Instant startsAt,
      MatchStatus status,
      String sourceNamespace,
      String providerStatus,
      boolean adminSuspended,
      boolean structuralConflict,
      Instant manualReviewedAt,
      Instant requestAttemptAt,
      Instant lastSuccessfulSyncAt,
      Instant providerUpdatedAt) {}

  public record MarketView(
      UUID id,
      UUID matchId,
      long matchVersion,
      long marketVersion,
      String marketType,
      String displayName,
      String period,
      String line,
      String participant,
      String ruleKey,
      String ruleText,
      String specifiersKey,
      String sourceNamespace,
      String providerStatus,
      boolean adminSuspended,
      boolean curated,
      Instant manualReviewedAt,
      Instant requestAttemptAt,
      Instant lastSuccessfulSyncAt,
      Instant providerUpdatedAt) {}

  public record SelectionView(
      UUID id,
      UUID matchId,
      UUID marketId,
      long matchVersion,
      long marketVersion,
      long selectionVersion,
      String selectionCode,
      String displayName,
      String odds,
      String sourceNamespace,
      String providerStatus,
      boolean adminSuspended,
      boolean curated,
      Instant manualReviewedAt,
      Instant requestAttemptAt,
      Instant lastSuccessfulSyncAt,
      Instant providerUpdatedAt) {}
}
