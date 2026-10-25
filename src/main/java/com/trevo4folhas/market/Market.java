package com.trevo4folhas.market;

import java.time.Instant;

import com.trevo4folhas.match.Match;
import com.trevo4folhas.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * A market instance offered for a match ({@code markets}). Identity is the composite key (match,
 * source namespace, bookmaker, external market id, period, line, participant, rule key, specifiers
 * key); display labels never take part in it. Non-applicable identity components use the canonical
 * sentinel {@code NONE}, and {@code line} is a canonical decimal without redundant zeros so absence
 * ({@code NONE}) stays distinct from zero ({@code 0}).
 */
@Entity
@Table(
    name = "markets",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_markets_identity",
          columnNames = {
            "match_id",
            "source_namespace",
            "bookmaker_id",
            "external_market_id",
            "period",
            "line",
            "participant",
            "rule_key",
            "specifiers_key"
          })
    })
public class Market extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "match_id", nullable = false)
  private Match match;

  @Column(name = "source_namespace", nullable = false)
  private String sourceNamespace;

  @Column(name = "bookmaker_id", nullable = false)
  private String bookmakerId;

  @Column(name = "external_market_id", nullable = false)
  private String externalMarketId;

  @Column(name = "market_type", nullable = false)
  private String marketType;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "period", nullable = false, length = 64)
  private String period;

  @Column(name = "line", nullable = false, length = 64)
  private String line;

  @Column(name = "participant", nullable = false)
  private String participant;

  @Column(name = "specifiers_key", nullable = false, columnDefinition = "text")
  private String specifiersKey;

  @Column(name = "rule_key", nullable = false)
  private String ruleKey;

  @Column(name = "rule_text", columnDefinition = "text")
  private String ruleText;

  @Column(name = "provider_status", nullable = false)
  private String providerStatus;

  @Column(name = "admin_suspended", nullable = false)
  private boolean adminSuspended;

  @Column(name = "curated", nullable = false)
  private boolean curated;

  /**
   * Operational metadata columns ({@code request_attempt_at}, {@code last_successful_sync_at},
   * {@code provider_updated_at}, {@code manual_reviewed_at}) are bookkeeping, never commercial
   * state. They are written exclusively through the repository bulk updates, which never renew the
   * commercial {@code version}, so JPA maps them {@code updatable = false}: a commercial flush can
   * never rewrite them with a stale persistence-context snapshot, neither in the same transaction
   * nor from another transaction holding an older snapshot. Bulk updates bypass the persistence
   * context, so managed instances keep their in-memory value until re-read and the database row is
   * authoritative.
   */
  @Column(name = "request_attempt_at", updatable = false)
  private Instant requestAttemptAt;

  @Column(name = "last_successful_sync_at", updatable = false)
  private Instant lastSuccessfulSyncAt;

  @Column(name = "provider_updated_at", updatable = false)
  private Instant providerUpdatedAt;

  @Column(name = "manual_reviewed_at", updatable = false)
  private Instant manualReviewedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  protected Market() {
    // For JPA.
  }

  public Market(
      Match match,
      String sourceNamespace,
      String bookmakerId,
      String externalMarketId,
      String marketType,
      String displayName,
      String period,
      String line,
      String participant,
      String specifiersKey,
      String ruleKey,
      String ruleText,
      String providerStatus) {
    this.match = match;
    this.sourceNamespace = sourceNamespace;
    this.bookmakerId = bookmakerId;
    this.externalMarketId = externalMarketId;
    this.marketType = marketType;
    this.displayName = displayName;
    this.period = period;
    this.line = line;
    this.participant = participant;
    this.specifiersKey = specifiersKey;
    this.ruleKey = ruleKey;
    this.ruleText = ruleText;
    this.providerStatus = providerStatus;
  }

  public Match getMatch() {
    return match;
  }

  public String getSourceNamespace() {
    return sourceNamespace;
  }

  public String getBookmakerId() {
    return bookmakerId;
  }

  public String getExternalMarketId() {
    return externalMarketId;
  }

  public String getMarketType() {
    return marketType;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPeriod() {
    return period;
  }

  public String getLine() {
    return line;
  }

  public String getParticipant() {
    return participant;
  }

  public String getSpecifiersKey() {
    return specifiersKey;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getRuleText() {
    return ruleText;
  }

  public String getProviderStatus() {
    return providerStatus;
  }

  public boolean isAdminSuspended() {
    return adminSuspended;
  }

  public boolean isCurated() {
    return curated;
  }

  public Instant getRequestAttemptAt() {
    return requestAttemptAt;
  }

  public Instant getLastSuccessfulSyncAt() {
    return lastSuccessfulSyncAt;
  }

  public Instant getProviderUpdatedAt() {
    return providerUpdatedAt;
  }

  public Instant getManualReviewedAt() {
    return manualReviewedAt;
  }

  public Long getVersion() {
    return version;
  }

  /** Label change: commercial metadata, renews the commercial version. */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** Curation change: renews the commercial version. */
  public void setCurated(boolean curated) {
    this.curated = curated;
  }
}
