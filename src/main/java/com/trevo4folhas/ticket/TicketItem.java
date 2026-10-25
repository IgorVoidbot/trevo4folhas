package com.trevo4folhas.ticket;

import java.math.BigDecimal;
import java.time.Instant;

import com.trevo4folhas.market.MarketSelection;
import com.trevo4folhas.match.Match;
import com.trevo4folhas.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One selection inside a pre-ticket ({@code ticket_items}), carrying immutable snapshots of the
 * accepted catalog state. Snapshot columns have no mutator: nothing in the application may rewrite
 * the terms the bettor accepted. There is at most one item per match inside a ticket.
 *
 * <p>{@code TicketItem} has no {@code version} column in the schema; immutability is enforced by
 * the absence of mutators plus the database constraint triggers from V8.
 */
@Entity
@Table(
    name = "ticket_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_ticket_items_ticket_match",
          columnNames = {"ticket_id", "match_id"})
    })
public class TicketItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "match_id", nullable = false)
  private Match match;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "market_selection_id", nullable = false)
  private MarketSelection marketSelection;

  @Column(name = "selected_odds", nullable = false, precision = 5, scale = 2)
  private BigDecimal selectedOdds;

  @Column(name = "match_version_snapshot", nullable = false)
  private long matchVersionSnapshot;

  @Column(name = "market_version_snapshot", nullable = false)
  private long marketVersionSnapshot;

  @Column(name = "selection_version_snapshot", nullable = false)
  private long selectionVersionSnapshot;

  @Column(name = "market_snapshot", nullable = false)
  private String marketSnapshot;

  @Column(name = "selection_snapshot", nullable = false)
  private String selectionSnapshot;

  @Column(name = "selection_code_snapshot", nullable = false, length = 64)
  private String selectionCodeSnapshot;

  @Column(name = "period_snapshot", nullable = false, length = 64)
  private String periodSnapshot;

  @Column(name = "line_snapshot", nullable = false, length = 64)
  private String lineSnapshot;

  @Column(name = "participant_snapshot", nullable = false)
  private String participantSnapshot;

  @Column(name = "rule_snapshot", columnDefinition = "text")
  private String ruleSnapshot;

  @Column(name = "provider_snapshot", nullable = false)
  private String providerSnapshot;

  @Column(name = "bookmaker_snapshot", nullable = false)
  private String bookmakerSnapshot;

  @Column(name = "external_fixture_id_snapshot", nullable = false)
  private String externalFixtureIdSnapshot;

  @Column(name = "external_market_id_snapshot", nullable = false)
  private String externalMarketIdSnapshot;

  @Column(name = "external_selection_key_snapshot", nullable = false)
  private String externalSelectionKeySnapshot;

  @Column(name = "specifiers_snapshot", nullable = false, columnDefinition = "text")
  private String specifiersSnapshot;

  @Column(name = "competition_snapshot", nullable = false, length = 120)
  private String competitionSnapshot;

  @Column(name = "home_team_snapshot", nullable = false, length = 120)
  private String homeTeamSnapshot;

  @Column(name = "away_team_snapshot", nullable = false, length = 120)
  private String awayTeamSnapshot;

  @Column(name = "match_date_snapshot", nullable = false)
  private Instant matchDateSnapshot;

  protected TicketItem() {
    // For JPA.
  }

  public TicketItem(
      Ticket ticket,
      Match match,
      MarketSelection marketSelection,
      BigDecimal selectedOdds,
      long matchVersionSnapshot,
      long marketVersionSnapshot,
      long selectionVersionSnapshot,
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
      String competitionSnapshot,
      String homeTeamSnapshot,
      String awayTeamSnapshot,
      Instant matchDateSnapshot) {
    this.ticket = ticket;
    this.match = match;
    this.marketSelection = marketSelection;
    this.selectedOdds = selectedOdds;
    this.matchVersionSnapshot = matchVersionSnapshot;
    this.marketVersionSnapshot = marketVersionSnapshot;
    this.selectionVersionSnapshot = selectionVersionSnapshot;
    this.marketSnapshot = marketSnapshot;
    this.selectionSnapshot = selectionSnapshot;
    this.selectionCodeSnapshot = selectionCodeSnapshot;
    this.periodSnapshot = periodSnapshot;
    this.lineSnapshot = lineSnapshot;
    this.participantSnapshot = participantSnapshot;
    this.ruleSnapshot = ruleSnapshot;
    this.providerSnapshot = providerSnapshot;
    this.bookmakerSnapshot = bookmakerSnapshot;
    this.externalFixtureIdSnapshot = externalFixtureIdSnapshot;
    this.externalMarketIdSnapshot = externalMarketIdSnapshot;
    this.externalSelectionKeySnapshot = externalSelectionKeySnapshot;
    this.specifiersSnapshot = specifiersSnapshot;
    this.competitionSnapshot = competitionSnapshot;
    this.homeTeamSnapshot = homeTeamSnapshot;
    this.awayTeamSnapshot = awayTeamSnapshot;
    this.matchDateSnapshot = matchDateSnapshot;
  }

  public Ticket getTicket() {
    return ticket;
  }

  public Match getMatch() {
    return match;
  }

  public MarketSelection getMarketSelection() {
    return marketSelection;
  }

  public BigDecimal getSelectedOdds() {
    return selectedOdds;
  }

  public long getMatchVersionSnapshot() {
    return matchVersionSnapshot;
  }

  public long getMarketVersionSnapshot() {
    return marketVersionSnapshot;
  }

  public long getSelectionVersionSnapshot() {
    return selectionVersionSnapshot;
  }

  public String getMarketSnapshot() {
    return marketSnapshot;
  }

  public String getSelectionSnapshot() {
    return selectionSnapshot;
  }

  public String getSelectionCodeSnapshot() {
    return selectionCodeSnapshot;
  }

  public String getPeriodSnapshot() {
    return periodSnapshot;
  }

  public String getLineSnapshot() {
    return lineSnapshot;
  }

  public String getParticipantSnapshot() {
    return participantSnapshot;
  }

  public String getRuleSnapshot() {
    return ruleSnapshot;
  }

  public String getProviderSnapshot() {
    return providerSnapshot;
  }

  public String getBookmakerSnapshot() {
    return bookmakerSnapshot;
  }

  public String getExternalFixtureIdSnapshot() {
    return externalFixtureIdSnapshot;
  }

  public String getExternalMarketIdSnapshot() {
    return externalMarketIdSnapshot;
  }

  public String getExternalSelectionKeySnapshot() {
    return externalSelectionKeySnapshot;
  }

  public String getSpecifiersSnapshot() {
    return specifiersSnapshot;
  }

  public String getCompetitionSnapshot() {
    return competitionSnapshot;
  }

  public String getHomeTeamSnapshot() {
    return homeTeamSnapshot;
  }

  public String getAwayTeamSnapshot() {
    return awayTeamSnapshot;
  }

  public Instant getMatchDateSnapshot() {
    return matchDateSnapshot;
  }
}
