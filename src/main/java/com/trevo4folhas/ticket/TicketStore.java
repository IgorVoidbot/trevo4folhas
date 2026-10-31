package com.trevo4folhas.ticket;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trevo4folhas.ticket.dto.TicketItemSnapshotDto;

@Repository
public class TicketStore {
  record Stored(
      UUID id,
      String code,
      String requestHash,
      String sessionHash,
      String name,
      String phone,
      BigDecimal amount,
      BigDecimal odds,
      BigDecimal potentialReturn,
      TicketStatus status,
      Instant createdAt,
      Instant expiresAt,
      String rulesVersion,
      Instant clickedAt) {
    @Override
    public String toString() {
      return "StoredTicket[redacted]";
    }
  }

  record Offer(
      UUID selectionId,
      UUID marketId,
      UUID matchId,
      long matchVersion,
      long marketVersion,
      long selectionVersion,
      BigDecimal odds,
      Instant startsAt) {}

  private final NamedParameterJdbcTemplate jdbc;

  public TicketStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void coordinate(UUID key) {
    jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '2s'");
    jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = '4s'");
    jdbc.query(
        "SELECT pg_advisory_xact_lock(:key)",
        Map.of("key", key.getMostSignificantBits() ^ key.getLeastSignificantBits()),
        rs -> {});
  }

  public Stored find(UUID key, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM public.tickets WHERE request_key=:key" + (lock ? " FOR UPDATE" : ""),
            Map.of("key", key),
            (rs, n) ->
                new Stored(
                    rs.getObject("id", UUID.class),
                    rs.getString("ticket_code"),
                    rs.getString("request_hash"),
                    rs.getString("session_hash"),
                    rs.getString("bettor_name"),
                    rs.getString("bettor_phone"),
                    rs.getBigDecimal("stake_amount"),
                    rs.getBigDecimal("total_odds"),
                    rs.getBigDecimal("potential_return"),
                    TicketStatus.valueOf(rs.getString("status")),
                    rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getString("rules_version"),
                    instant(rs, "whatsapp_clicked_at")))
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<Offer> offers(Collection<UUID> ids) {
    return jdbc.query(
        """
        SELECT s.id, k.id AS market_id, m.id AS match_id, m.version AS match_version,
          k.version AS market_version, s.version AS selection_version, s.odds, m.match_date
        FROM public.market_selections s JOIN public.markets k ON k.id=s.market_id
        JOIN public.matches m ON m.id=k.match_id WHERE s.id IN (:ids) ORDER BY m.id
        """,
        Map.of("ids", ids),
        (rs, n) ->
            new Offer(
                rs.getObject("id", UUID.class),
                rs.getObject("market_id", UUID.class),
                rs.getObject("match_id", UUID.class),
                rs.getLong("match_version"),
                rs.getLong("market_version"),
                rs.getLong("selection_version"),
                rs.getBigDecimal("odds"),
                rs.getTimestamp("match_date").toInstant()));
  }

  public void lockCatalog(List<Offer> offers) {
    // Fixed identifiers only, and the same parent-before-child ordering used by edit/import.
    lock("matches", offers.stream().map(Offer::matchId).distinct().toList());
    lock("markets", offers.stream().map(Offer::marketId).distinct().toList());
    lock("market_selections", offers.stream().map(Offer::selectionId).distinct().toList());
  }

  private void lock(String table, List<UUID> ids) {
    int count =
        jdbc.queryForList(
                "SELECT id FROM public." + table + " WHERE id IN (:ids) ORDER BY id FOR UPDATE",
                Map.of("ids", ids),
                UUID.class)
            .size();
    if (count != ids.size()) throw TicketException.unavailable();
  }

  public Instant creationTime() {
    return jdbc.queryForObject(
        "SELECT CURRENT_TIMESTAMP", Map.of(), (rs, n) -> rs.getTimestamp(1).toInstant());
  }

  public void insert(
      UUID id,
      UUID key,
      String code,
      String sessionHash,
      TicketIntent intent,
      MoneyCalculator.Calculation calculation,
      Instant expiry) {
    jdbc.update(
        """
        INSERT INTO public.tickets(id,ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,
          stake_amount,total_odds,potential_return,status,expires_at,rules_version)
        VALUES(:id,:code,:key,:requestHash,:sessionHash,:name,:phone,:amount,:odds,:potentialReturn,'PENDING',:expiry,:rules)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("code", code)
            .addValue("key", key)
            .addValue("requestHash", intent.requestHash())
            .addValue("sessionHash", sessionHash)
            .addValue("name", intent.bettorName())
            .addValue("phone", intent.bettorPhone())
            .addValue("amount", intent.stakeAmount())
            .addValue("odds", calculation.totalOdds())
            .addValue("potentialReturn", calculation.potentialReturn())
            .addValue("expiry", Timestamp.from(expiry))
            .addValue("rules", intent.rulesVersion()));
    int inserted =
        jdbc.update(
            """
        INSERT INTO public.ticket_items(ticket_id,match_id,market_selection_id,selected_odds,
          match_version_snapshot,market_version_snapshot,selection_version_snapshot,market_snapshot,selection_snapshot,
          selection_code_snapshot,period_snapshot,line_snapshot,participant_snapshot,rule_snapshot,provider_snapshot,
          bookmaker_snapshot,external_fixture_id_snapshot,external_market_id_snapshot,external_selection_key_snapshot,
          specifiers_snapshot,competition_snapshot,home_team_snapshot,away_team_snapshot,match_date_snapshot)
        SELECT :ticket,m.id,s.id,s.odds,m.version,k.version,s.version,k.display_name,s.display_name,s.selection_code,
          k.period,k.line,k.participant,k.rule_text,k.source_namespace,k.bookmaker_id,m.external_fixture_id,
          k.external_market_id,s.external_selection_key,k.specifiers_key,m.competition,m.home_team,m.away_team,m.match_date
        FROM public.market_selections s JOIN public.markets k ON k.id=s.market_id JOIN public.matches m ON m.id=k.match_id
        WHERE s.id IN (:ids) ORDER BY m.id
        """,
            Map.of(
                "ticket",
                id,
                "ids",
                intent.items().stream().map(TicketRequests.Item::selectionId).toList()));
    if (inserted != intent.items().size()) throw TicketException.unavailable();
  }

  public List<TicketItemSnapshotDto> snapshots(UUID ticketId) {
    return jdbc.query(
        "SELECT * FROM public.ticket_items WHERE ticket_id=:id ORDER BY match_id",
        Map.of("id", ticketId),
        (rs, n) ->
            new TicketItemSnapshotDto(
                rs.getString("competition_snapshot"),
                rs.getString("home_team_snapshot"),
                rs.getString("away_team_snapshot"),
                rs.getTimestamp("match_date_snapshot").toInstant(),
                rs.getString("market_snapshot"),
                rs.getString("selection_snapshot"),
                rs.getString("selection_code_snapshot"),
                rs.getString("period_snapshot"),
                rs.getString("line_snapshot"),
                rs.getString("participant_snapshot"),
                rs.getString("rule_snapshot"),
                rs.getString("provider_snapshot"),
                rs.getString("bookmaker_snapshot"),
                rs.getString("external_fixture_id_snapshot"),
                rs.getString("external_market_id_snapshot"),
                rs.getString("external_selection_key_snapshot"),
                rs.getString("specifiers_snapshot"),
                rs.getBigDecimal("selected_odds"),
                rs.getLong("match_version_snapshot"),
                rs.getLong("market_version_snapshot"),
                rs.getLong("selection_version_snapshot")));
  }

  public void click(UUID id, Instant now) {
    jdbc.update(
        "UPDATE public.tickets SET whatsapp_clicked_at=:now WHERE id=:id AND whatsapp_clicked_at IS NULL",
        Map.of("id", id, "now", Timestamp.from(now)));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
