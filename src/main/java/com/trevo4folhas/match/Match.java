package com.trevo4folhas.match;

import java.time.Instant;

import com.trevo4folhas.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A football match ({@code matches}). The physical kickoff column is {@code match_date}. */
@Entity
@Table(name = "matches")
public class Match extends BaseEntity {

  @Column(name = "competition", nullable = false, length = 120)
  private String competition;

  @Column(name = "home_team", nullable = false, length = 120)
  private String homeTeam;

  @Column(name = "away_team", nullable = false, length = 120)
  private String awayTeam;

  /** Kickoff instant; the physical column is {@code match_date} (timestamptz, UTC). */
  @Column(name = "match_date", nullable = false)
  private Instant startsAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private MatchStatus status;

  /** Provider/product/channel namespace; {@code MANUAL} marks human-created catalog rows. */
  @Column(name = "source_namespace", nullable = false)
  private String sourceNamespace;

  @Column(name = "external_fixture_id", nullable = false)
  private String externalFixtureId;

  @Column(name = "provider_status", nullable = false)
  private String providerStatus;

  @Column(name = "admin_suspended", nullable = false)
  private boolean adminSuspended;

  @Column(name = "structural_conflict", nullable = false)
  private boolean structuralConflict;

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

  protected Match() {
    // For JPA.
  }

  public Match(
      String competition,
      String homeTeam,
      String awayTeam,
      Instant startsAt,
      MatchStatus status,
      String sourceNamespace,
      String externalFixtureId,
      String providerStatus) {
    this.competition = competition;
    this.homeTeam = homeTeam;
    this.awayTeam = awayTeam;
    this.startsAt = startsAt;
    this.status = status;
    this.sourceNamespace = sourceNamespace;
    this.externalFixtureId = externalFixtureId;
    this.providerStatus = providerStatus;
  }

  public String getCompetition() {
    return competition;
  }

  public String getHomeTeam() {
    return homeTeam;
  }

  public String getAwayTeam() {
    return awayTeam;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public MatchStatus getStatus() {
    return status;
  }

  public String getSourceNamespace() {
    return sourceNamespace;
  }

  public String getExternalFixtureId() {
    return externalFixtureId;
  }

  public String getProviderStatus() {
    return providerStatus;
  }

  public boolean isAdminSuspended() {
    return adminSuspended;
  }

  public boolean isStructuralConflict() {
    return structuralConflict;
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

  /** Commercial/state change: renews the commercial version. */
  public void changeStatus(MatchStatus newStatus) {
    this.status = newStatus;
  }

  /** Curation change: renews the commercial version. */
  public void setAdminSuspended(boolean adminSuspended) {
    this.adminSuspended = adminSuspended;
  }
}
