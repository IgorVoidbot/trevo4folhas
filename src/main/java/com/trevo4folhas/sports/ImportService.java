package com.trevo4folhas.sports;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trevo4folhas.catalog.CatalogDtos.*;
import com.trevo4folhas.catalog.CatalogRepository;
import com.trevo4folhas.sports.ProviderMapping.*;

/** Short transactions, one parent hierarchy per batch. No HTTP or ticket locks here. */
@Service
public class ImportService {
  private final NamedParameterJdbcTemplate jdbc;
  private final CatalogRepository catalog;
  private final Clock clock;

  public ImportService(NamedParameterJdbcTemplate jdbc, CatalogRepository catalog, Clock clock) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional(timeout = 5)
  public void fixture(Fixture row) {
    catalog.beginWrite();
    Instant now = clock.instant();
    var p =
        new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("external", row.externalId())
            .addValue("competition", row.competition())
            .addValue("home", row.home())
            .addValue("away", row.away())
            .addValue("start", Timestamp.from(row.startsAt()))
            .addValue("status", row.status())
            .addValue("now", Timestamp.from(now));
    jdbc.update(
        """
        INSERT INTO public.matches (id,competition,home_team,away_team,match_date,status,source_namespace,external_fixture_id,provider_status,request_attempt_at,last_successful_sync_at,provider_updated_at)
        VALUES (:id,:competition,:home,:away,:start,:status,'API_FOOTBALL',:external,:status,:now,:now,:now)
        ON CONFLICT (source_namespace,external_fixture_id) DO NOTHING
        """,
        p);
    UUID id = fixtureId(row.externalId());
    MatchView current = catalog.match(id, true);
    boolean structural =
        !current.competition().equals(row.competition())
            || !current.homeTeam().equals(row.home())
            || !current.awayTeam().equals(row.away())
            || !current.startsAt().equals(row.startsAt());
    boolean conflict = structural && catalog.matchReferenced(id);
    boolean preserve = conflict || current.structuralConflict();
    boolean terminal = current.status() == com.trevo4folhas.match.MatchStatus.CANCELLED;
    String localStatus =
        terminal
            ? "CANCELLED"
            : "CANCELLED".equals(row.status()) ? "CANCELLED" : current.status().name();
    String providerStatus =
        "CANCELLED".equals(current.providerStatus()) ? "CANCELLED" : row.status();
    p.addValue("id", id)
        .addValue("conflict", preserve)
        .addValue("localStatus", localStatus)
        .addValue("providerStatus", providerStatus);
    boolean changed =
        (structural && !preserve)
            || conflict
            || !current.providerStatus().equals(providerStatus)
            || !current.status().name().equals(localStatus);
    p.addValue("increment", changed ? 1 : 0);
    jdbc.update(
        """
        UPDATE public.matches SET competition=CASE WHEN :conflict THEN competition ELSE :competition END,
          home_team=CASE WHEN :conflict THEN home_team ELSE :home END,
          away_team=CASE WHEN :conflict THEN away_team ELSE :away END,
          match_date=CASE WHEN :conflict THEN match_date ELSE :start END,
          structural_conflict=:conflict, status=:localStatus, provider_status=:providerStatus,
          version=version+:increment, request_attempt_at=:now,last_successful_sync_at=:now,provider_updated_at=:now WHERE id=:id
        """,
        p);
  }

  @Transactional(timeout = 5)
  public boolean offer(Offer row) {
    catalog.beginWrite();
    UUID matchId = fixtureId(row.fixtureId());
    catalog.match(matchId, true);
    Instant now = clock.instant();
    // PostgreSQL stores microseconds. Compare the same precision we persist so a
    // replay cannot renew a price or bypass same-timestamp price-change detection.
    Instant updatedAt =
        row.updatedAt() == null
            ? null
            : row.updatedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var p =
        new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("match", matchId)
            .addValue("bookmaker", row.bookmakerId())
            .addValue("external", row.marketId())
            .addValue("type", row.marketType())
            .addValue("name", row.marketName())
            .addValue("period", row.period())
            .addValue("line", row.line())
            .addValue("participant", row.participant())
            .addValue("ruleKey", row.ruleKey())
            .addValue("rule", row.ruleText())
            .addValue("specifiers", row.specifiersKey())
            .addValue("now", Timestamp.from(now))
            .addValue("updated", updatedAt == null ? null : Timestamp.from(updatedAt));
    jdbc.update(
        """
        INSERT INTO public.markets (id,match_id,source_namespace,bookmaker_id,external_market_id,market_type,display_name,period,line,participant,rule_key,rule_text,specifiers_key,provider_status)
        VALUES (:id,:match,'API_FOOTBALL',:bookmaker,:external,:type,:name,:period,:line,:participant,:ruleKey,:rule,:specifiers,'OPEN')
        ON CONFLICT (match_id,source_namespace,bookmaker_id,external_market_id,period,line,participant,rule_key,specifiers_key) DO NOTHING
        """,
        p);
    UUID marketId =
        jdbc.queryForObject(
            """
        SELECT id FROM public.markets WHERE match_id=:match AND source_namespace='API_FOOTBALL' AND bookmaker_id=:bookmaker AND external_market_id=:external
        AND period=:period AND line=:line AND participant=:participant AND rule_key=:ruleKey AND specifiers_key=:specifiers
        """,
            p,
            UUID.class);
    MarketView market = catalog.market(marketId, true);
    boolean termsConflict =
        !market.marketType().equals(row.marketType())
            || !market.displayName().equals(row.marketName())
            || !Objects.equals(market.ruleText(), row.ruleText());
    p.addValue("market", marketId).addValue("key", row.selectionKey());
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT id FROM public.market_selections WHERE market_id=:market AND external_selection_key=:key",
            p,
            UUID.class);
    boolean valid =
        !termsConflict
            && row.odds() != null
            && row.updatedAt() != null
            && !row.updatedAt().isAfter(now);
    UUID selectionId = ids.isEmpty() ? UUID.randomUUID() : ids.get(0);
    p.addValue("id", selectionId)
        .addValue("code", row.selectionCode())
        .addValue("selectionName", row.selectionName())
        .addValue("odds", row.odds());
    if (ids.isEmpty() && valid) {
      jdbc.update(
          """
          INSERT INTO public.market_selections (id,market_id,external_selection_key,selection_code,display_name,odds,provider_status)
          VALUES (:id,:market,:key,:code,:selectionName,:odds,'OPEN')
          """,
          p);
    } else if (ids.isEmpty()) {
      jdbc.update(
          "UPDATE public.markets SET request_attempt_at=:now, provider_status='INVALID', version=version+CASE WHEN provider_status='INVALID' THEN 0 ELSE 1 END WHERE id=:market",
          p);
      return false;
    }
    SelectionView selection = catalog.selection(selectionId, true);
    Instant previous =
        jdbc.queryForObject(
            "SELECT provider_updated_at FROM public.market_selections WHERE id=:id",
            p,
            (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant());
    boolean regression =
        previous != null && (row.updatedAt() == null || updatedAt.isBefore(previous));
    boolean sameTimestampPriceChange =
        previous != null
            && previous.equals(updatedAt)
            && row.odds() != null
            && new java.math.BigDecimal(selection.odds()).compareTo(row.odds()) != 0;
    boolean selectionConflict =
        !selection.displayName().equals(row.selectionName())
            || !selection.selectionCode().equals(row.selectionCode());
    valid = valid && !regression && !sameTimestampPriceChange && !selectionConflict;
    p.addValue("valid", valid).addValue("status", valid ? "OPEN" : "INVALID");
    boolean changed =
        !selection.providerStatus().equals(valid ? "OPEN" : "INVALID")
            || (valid && new java.math.BigDecimal(selection.odds()).compareTo(row.odds()) != 0);
    p.addValue("increment", changed ? 1 : 0);
    jdbc.update(
        """
        UPDATE public.market_selections SET odds=CASE WHEN :valid THEN :odds ELSE odds END,
          provider_status=:status,version=version+:increment,request_attempt_at=:now,
          last_successful_sync_at=CASE WHEN :valid THEN :now ELSE last_successful_sync_at END,
          provider_updated_at=CASE WHEN :valid THEN :updated ELSE provider_updated_at END WHERE id=:id
        """,
        p);
    jdbc.update(
        """
        UPDATE public.markets SET request_attempt_at=:now,
          provider_status=CASE WHEN :valid THEN 'OPEN' ELSE 'INVALID' END,
          version=version+CASE WHEN provider_status=:status THEN 0 ELSE 1 END,
          last_successful_sync_at=CASE WHEN :valid THEN :now ELSE last_successful_sync_at END,
          provider_updated_at=CASE WHEN :valid AND (provider_updated_at IS NULL OR :updated>provider_updated_at) THEN :updated ELSE provider_updated_at END WHERE id=:market
        """,
        p);
    return valid;
  }

  private UUID fixtureId(String external) {
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT id FROM public.matches WHERE source_namespace='API_FOOTBALL' AND external_fixture_id=:external",
            Map.of("external", external),
            UUID.class);
    if (ids.isEmpty()) throw new SyncFailure("FIXTURE_NOT_IMPORTED");
    return ids.get(0);
  }
}
