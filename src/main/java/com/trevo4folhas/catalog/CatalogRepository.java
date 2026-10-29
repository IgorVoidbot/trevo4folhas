package com.trevo4folhas.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trevo4folhas.catalog.CatalogDtos.MatchView;
import com.trevo4folhas.catalog.CatalogRequests.CreateMatch;
import com.trevo4folhas.catalog.CatalogRequests.CreateMarket;
import com.trevo4folhas.catalog.CatalogRequests.CreateSelection;
import com.trevo4folhas.catalog.CatalogDtos.MarketView;
import com.trevo4folhas.catalog.CatalogDtos.SelectionView;
import com.trevo4folhas.match.MatchStatus;

/** Local PostgreSQL read/write queries; named parameters keep identity and binding order explicit. */
@Repository
public class CatalogRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public CatalogRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertMatch(UUID id, CreateMatch request, Instant reviewedAt) {
    jdbc.update("""
        INSERT INTO public.matches
          (id, competition, home_team, away_team, match_date, status,
           source_namespace, external_fixture_id, provider_status, manual_reviewed_at)
        VALUES (:id, :competition, :home, :away, :startsAt, 'OPEN',
                'MANUAL', :externalId, 'OPEN', :reviewedAt)
        """, Map.of("id", id, "competition", request.competition().strip(),
        "home", request.homeTeam().strip(), "away", request.awayTeam().strip(),
        "startsAt", Timestamp.from(request.startsAt()), "externalId", UUID.randomUUID().toString(),
        "reviewedAt", Timestamp.from(reviewedAt)));
  }

  public MatchView match(UUID id, boolean lock) {
    return jdbc.query("SELECT * FROM public.matches WHERE id = :id" + (lock ? " FOR UPDATE" : ""),
        Map.of("id", id), this::mapMatch).stream().findFirst().orElseThrow(() ->
        new CatalogException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Partida não encontrada."));
  }

  public void beginWrite() {
    jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '2s'");
  }

  public void insertMarket(UUID id, UUID matchId, CreateMarket request,
      String line, String participant, String specifiersKey, Instant reviewedAt) {
    var parameters = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
        .addValue("id", id).addValue("matchId", matchId).addValue("type", request.marketType())
        .addValue("name", request.displayName().strip()).addValue("period", request.period().strip())
        .addValue("line", line).addValue("participant", participant).addValue("specifiers", specifiersKey)
        .addValue("ruleKey", request.ruleKey().strip()).addValue("ruleText", request.ruleText().strip())
        .addValue("curated", request.curated()).addValue("reviewedAt", Timestamp.from(reviewedAt));
    jdbc.update("""
        INSERT INTO public.markets (id, match_id, source_namespace, bookmaker_id, external_market_id,
          market_type, display_name, period, line, participant, specifiers_key, rule_key, rule_text,
          provider_status, curated, manual_reviewed_at)
        VALUES (:id, :matchId, 'MANUAL', 'NONE', :type, :type, :name, :period, :line,
          :participant, :specifiers, :ruleKey, :ruleText, 'OPEN', :curated, :reviewedAt)
        """, parameters);
  }

  public MarketView market(UUID id, boolean lock) {
    return jdbc.query("""
        SELECT k.*, m.version AS match_version FROM public.markets k
        JOIN public.matches m ON m.id = k.match_id WHERE k.id = :id
        """ + (lock ? " FOR UPDATE OF k" : ""), Map.of("id", id), this::mapMarket)
        .stream().findFirst().orElseThrow(() -> new CatalogException(
            HttpStatus.NOT_FOUND, "MARKET_NOT_FOUND", "Mercado não encontrado."));
  }

  public void insertSelection(UUID id, UUID marketId, CreateSelection request, Instant reviewedAt) {
    jdbc.update("""
        INSERT INTO public.market_selections (id, market_id, external_selection_key,
          selection_code, display_name, odds, provider_status, curated, manual_reviewed_at)
        VALUES (:id, :marketId, :code, :code, :name, :odds, 'OPEN', :curated, :reviewedAt)
        """, Map.of("id", id, "marketId", marketId, "code", request.selectionCode(),
        "name", request.displayName().strip(), "odds", request.odds(),
        "curated", request.curated(), "reviewedAt", Timestamp.from(reviewedAt)));
  }

  public SelectionView selection(UUID id, boolean lock) {
    return jdbc.query("""
        SELECT s.*, k.match_id, k.source_namespace, k.version AS market_version,
          m.version AS match_version FROM public.market_selections s
        JOIN public.markets k ON k.id = s.market_id JOIN public.matches m ON m.id = k.match_id
        WHERE s.id = :id
        """ + (lock ? " FOR UPDATE OF s" : ""), Map.of("id", id), this::mapSelection)
        .stream().findFirst().orElseThrow(() -> new CatalogException(
            HttpStatus.NOT_FOUND, "SELECTION_NOT_FOUND", "Seleção não encontrada."));
  }

  private MarketView mapMarket(ResultSet row, int index) throws SQLException {
    return new MarketView(row.getObject("id", UUID.class), row.getObject("match_id", UUID.class),
        row.getLong("match_version"), row.getLong("version"), row.getString("market_type"),
        row.getString("display_name"), row.getString("period"), row.getString("line"),
        row.getString("participant"), row.getString("rule_key"), row.getString("rule_text"),
        row.getString("specifiers_key"), row.getString("source_namespace"), row.getString("provider_status"),
        row.getBoolean("admin_suspended"), row.getBoolean("curated"), instant(row, "manual_reviewed_at"));
  }

  private SelectionView mapSelection(ResultSet row, int index) throws SQLException {
    return new SelectionView(row.getObject("id", UUID.class), row.getObject("match_id", UUID.class),
        row.getObject("market_id", UUID.class), row.getLong("match_version"), row.getLong("market_version"),
        row.getLong("version"), row.getString("selection_code"), row.getString("display_name"),
        row.getBigDecimal("odds").toPlainString(), row.getString("source_namespace"),
        row.getString("provider_status"), row.getBoolean("admin_suspended"), row.getBoolean("curated"),
        instant(row, "manual_reviewed_at"));
  }

  private MatchView mapMatch(ResultSet row, int index) throws SQLException {
    return new MatchView(row.getObject("id", UUID.class), row.getLong("version"),
        row.getString("competition"), row.getString("home_team"), row.getString("away_team"),
        row.getTimestamp("match_date").toInstant(), MatchStatus.valueOf(row.getString("status")),
        row.getString("source_namespace"), row.getString("provider_status"),
        row.getBoolean("admin_suspended"), row.getBoolean("structural_conflict"),
        instant(row, "manual_reviewed_at"));
  }

  private Instant instant(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
