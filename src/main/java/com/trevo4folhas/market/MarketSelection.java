package com.trevo4folhas.market;

import java.math.BigDecimal;
import java.time.Instant;

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
 * A selection inside a market ({@code market_selections}). {@code HOME}/{@code DRAW}/{@code AWAY}
 * are selection codes of the 1X2 market only, never a universal enum.
 */
@Entity
@Table(
    name = "market_selections",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_market_selections_identity",
          columnNames = {"market_id", "external_selection_key"})
    })
public class MarketSelection extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "market_id", nullable = false)
  private Market market;

  @Column(name = "external_selection_key", nullable = false)
  private String externalSelectionKey;

  @Column(name = "selection_code", nullable = false, length = 64)
  private String selectionCode;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "odds", nullable = false, precision = 5, scale = 2)
  private BigDecimal odds;

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

  protected MarketSelection() {
    // For JPA.
  }

  public MarketSelection(
      Market market,
      String externalSelectionKey,
      String selectionCode,
      String displayName,
      BigDecimal odds,
      String providerStatus) {
    this.market = market;
    this.externalSelectionKey = externalSelectionKey;
    this.selectionCode = selectionCode;
    this.displayName = displayName;
    this.odds = odds;
    this.providerStatus = providerStatus;
  }

  public Market getMarket() {
    return market;
  }

  public String getExternalSelectionKey() {
    return externalSelectionKey;
  }

  public String getSelectionCode() {
    return selectionCode;
  }

  public String getDisplayName() {
    return displayName;
  }

  public BigDecimal getOdds() {
    return odds;
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

  /** Price change: commercial change, renews the commercial version of the selection. */
  public void updateOdds(BigDecimal newOdds) {
    this.odds = newOdds;
  }

  /** Curation change: renews the commercial version. */
  public void setCurated(boolean curated) {
    this.curated = curated;
  }
}
