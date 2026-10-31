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

import com.trevo4folhas.catalog.CatalogDtos.MarketView;
import com.trevo4folhas.catalog.CatalogDtos.MatchView;
import com.trevo4folhas.catalog.CatalogDtos.SelectionView;
import com.trevo4folhas.catalog.CatalogRequests.CreateMarket;
import com.trevo4folhas.catalog.CatalogRequests.CreateMatch;
import com.trevo4folhas.catalog.CatalogRequests.CreateSelection;
import com.trevo4folhas.match.MatchStatus;

/**
 * Local PostgreSQL read/write queries; named parameters keep identity and binding order explicit.
 */
@Repository
public class CatalogRepository {
  private final NamedParameterJdbcTemplate jdbc;

  private final CatalogProperties settings;
  private final org.springframework.core.env.Environment environment;

  public CatalogRepository(
      NamedParameterJdbcTemplate jdbc,
      CatalogProperties settings,
      org.springframework.core.env.Environment environment) {
    this.jdbc = jdbc;
    this.settings = settings;
    this.environment = environment;
  }

  public void insertMatch(UUID id, CreateMatch request, Instant reviewedAt) {
    jdbc.update(
        """
        INSERT INTO public.matches
          (id, competition, home_team, away_team, match_date, status,
           source_namespace, external_fixture_id, provider_status, manual_reviewed_at)
        VALUES (:id, :competition, :home, :away, :startsAt, 'OPEN',
                'MANUAL', :externalId, 'OPEN', :reviewedAt)
        """,
        Map.of(
            "id",
            id,
            "competition",
            request.competition().strip(),
            "home",
            request.homeTeam().strip(),
            "away",
            request.awayTeam().strip(),
            "startsAt",
            Timestamp.from(request.startsAt()),
            "externalId",
            UUID.randomUUID().toString(),
            "reviewedAt",
            Timestamp.from(reviewedAt)));
  }

  public MatchView match(UUID id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM public.matches WHERE id = :id" + (lock ? " FOR UPDATE" : ""),
            Map.of("id", id),
            this::mapMatch)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new CatalogException(
                    HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Partida não encontrada."));
  }

  public void beginWrite() {
    jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '2s'");
  }

  public void insertMarket(
      UUID id,
      UUID matchId,
      CreateMarket request,
      String line,
      String participant,
      String specifiersKey,
      Instant reviewedAt) {
    var parameters =
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("id", id)
            .addValue("matchId", matchId)
            .addValue("type", request.marketType())
            .addValue("name", request.displayName().strip())
            .addValue("period", request.period().strip())
            .addValue("line", line)
            .addValue("participant", participant)
            .addValue("specifiers", specifiersKey)
            .addValue("ruleKey", request.ruleKey().strip())
            .addValue("ruleText", request.ruleText().strip())
            .addValue("curated", request.curated())
            .addValue("reviewedAt", Timestamp.from(reviewedAt));
    jdbc.update(
        """
        INSERT INTO public.markets (id, match_id, source_namespace, bookmaker_id, external_market_id,
          market_type, display_name, period, line, participant, specifiers_key, rule_key, rule_text,
          provider_status, curated, manual_reviewed_at)
        VALUES (:id, :matchId, 'MANUAL', 'NONE', :type, :type, :name, :period, :line,
          :participant, :specifiers, :ruleKey, :ruleText, 'OPEN', :curated, :reviewedAt)
        """,
        parameters);
  }

  public MarketView market(UUID id, boolean lock) {
    return jdbc
        .query(
            """
        SELECT k.*, m.version AS match_version FROM public.markets k
        JOIN public.matches m ON m.id = k.match_id WHERE k.id = :id
        """
                + (lock ? " FOR UPDATE OF k" : ""),
            Map.of("id", id),
            this::mapMarket)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new CatalogException(
                    HttpStatus.NOT_FOUND, "MARKET_NOT_FOUND", "Mercado não encontrado."));
  }

  public void insertSelection(UUID id, UUID marketId, CreateSelection request, Instant reviewedAt) {
    jdbc.update(
        """
        INSERT INTO public.market_selections (id, market_id, external_selection_key,
          selection_code, display_name, odds, provider_status, curated, manual_reviewed_at)
        VALUES (:id, :marketId, :code, :code, :name, :odds, 'OPEN', :curated, :reviewedAt)
        """,
        Map.of(
            "id",
            id,
            "marketId",
            marketId,
            "code",
            request.selectionCode(),
            "name",
            request.displayName().strip(),
            "odds",
            request.odds(),
            "curated",
            request.curated(),
            "reviewedAt",
            Timestamp.from(reviewedAt)));
  }

  public SelectionView selection(UUID id, boolean lock) {
    return jdbc
        .query(
            """
        SELECT s.*, k.match_id, k.source_namespace, k.version AS market_version,
          m.version AS match_version FROM public.market_selections s
        JOIN public.markets k ON k.id = s.market_id JOIN public.matches m ON m.id = k.match_id
        WHERE s.id = :id
        """
                + (lock ? " FOR UPDATE OF s" : ""),
            Map.of("id", id),
            this::mapSelection)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new CatalogException(
                    HttpStatus.NOT_FOUND, "SELECTION_NOT_FOUND", "Seleção não encontrada."));
  }

  public boolean matchReferenced(UUID id) {
    return jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM public.ticket_items WHERE match_id=:id)",
        Map.of("id", id),
        Boolean.class);
  }

  public boolean marketReferenced(UUID id) {
    return jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM public.ticket_items i JOIN public.market_selections s ON s.id=i.market_selection_id WHERE s.market_id=:id)",
        Map.of("id", id),
        Boolean.class);
  }

  public boolean selectionReferenced(UUID id) {
    return jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM public.ticket_items WHERE market_selection_id=:id)",
        Map.of("id", id),
        Boolean.class);
  }

  public void updateMatch(UUID id, CatalogRequests.UpdateMatch r, Instant now, boolean manual) {
    jdbc.update(
        """
        UPDATE public.matches SET competition=:competition, home_team=:home, away_team=:away,
          match_date=:startsAt, status=:status, admin_suspended=:suspended, version=version+1,
          manual_reviewed_at=CASE WHEN :manual THEN :now ELSE manual_reviewed_at END WHERE id=:id
        """,
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("id", id)
            .addValue("competition", r.competition().strip())
            .addValue("home", r.homeTeam().strip())
            .addValue("away", r.awayTeam().strip())
            .addValue("startsAt", Timestamp.from(r.startsAt()))
            .addValue("status", r.status().name())
            .addValue("suspended", r.adminSuspended())
            .addValue("manual", manual)
            .addValue("now", Timestamp.from(now)));
  }

  public void updateMarket(UUID id, CatalogRequests.UpdateMarket r, Instant now, boolean manual) {
    jdbc.update(
        """
        UPDATE public.markets SET display_name=:name, rule_text=:rule, curated=:curated,
          admin_suspended=:suspended, version=version+1,
          manual_reviewed_at=CASE WHEN :manual THEN :now ELSE manual_reviewed_at END WHERE id=:id
        """,
        Map.of(
            "id",
            id,
            "name",
            r.displayName().strip(),
            "rule",
            r.ruleText().strip(),
            "curated",
            r.curated(),
            "suspended",
            r.adminSuspended(),
            "manual",
            manual,
            "now",
            Timestamp.from(now)));
  }

  public void updateSelection(
      UUID id, CatalogRequests.UpdateSelection r, Instant now, boolean manual) {
    jdbc.update(
        """
        UPDATE public.market_selections SET display_name=:name, odds=:odds, curated=:curated,
          admin_suspended=:suspended, version=version+1,
          manual_reviewed_at=CASE WHEN :manual THEN :now ELSE manual_reviewed_at END WHERE id=:id
        """,
        Map.of(
            "id",
            id,
            "name",
            r.displayName().strip(),
            "odds",
            r.odds(),
            "curated",
            r.curated(),
            "suspended",
            r.adminSuspended(),
            "manual",
            manual,
            "now",
            Timestamp.from(now)));
  }

  // Only fixed identifiers enter these SQL fragments. All caller input is bound.
  private String fresh(String alias, String manualCutoff, String externalCutoff, String source) {
    return "(("
        + source
        + "='MANUAL' AND "
        + alias
        + ".manual_reviewed_at > :"
        + manualCutoff
        + " AND "
        + alias
        + ".manual_reviewed_at <= :now) OR ("
        + source
        + "<>'MANUAL' AND "
        + alias
        + ".provider_updated_at > :"
        + externalCutoff
        + " AND "
        + alias
        + ".provider_updated_at <= :now AND "
        + alias
        + ".last_successful_sync_at > :"
        + externalCutoff
        + " AND "
        + alias
        + ".last_successful_sync_at <= :now))";
  }

  private String matchEligibility() {
    return "m.status='OPEN' AND m.provider_status='OPEN' AND NOT m.admin_suspended AND NOT m.structural_conflict AND m.match_date>:now AND "
        + fresh("m", "manualSchedule", "externalSchedule", "m.source_namespace")
        + " AND "
        + fresh("m", "manualStatus", "externalStatus", "m.source_namespace");
  }

  private String marketEligibility() {
    return "k.curated AND NOT k.admin_suspended AND k.provider_status='OPEN' AND k.rule_text IS NOT NULL AND btrim(k.rule_text)<>'' AND "
        + fresh("k", "manualPrice", "externalPrice", "k.source_namespace");
  }

  private String selectionEligibility() {
    return "s.curated AND NOT s.admin_suspended AND s.provider_status='OPEN' AND "
        + fresh("s", "manualPrice", "externalPrice", "k.source_namespace");
  }

  private org.springframework.jdbc.core.namedparam.MapSqlParameterSource parameters(Instant now) {
    var p =
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("now", Timestamp.from(now));
    p.addValue("manualSchedule", cutoff(now, settings.manualScheduleMaxAgeSeconds()));
    p.addValue("manualStatus", cutoff(now, settings.manualStatusMaxAgeSeconds()));
    p.addValue("manualPrice", cutoff(now, settings.manualPriceMaxAgeSeconds()));
    for (String kind : java.util.List.of("Schedule", "Status", "Price")) {
      p.addValue(
          "external" + kind,
          cutoff(
              now,
              environment.getProperty(
                  "trevo.sports.max-" + kind.toLowerCase(java.util.Locale.ROOT) + "-age-seconds",
                  Long.class,
                  0L)));
    }
    return p;
  }

  private Timestamp cutoff(Instant now, Long age) {
    return Timestamp.from(now.minusSeconds(age == null || age < 0 ? 0 : Math.min(age, 31536000L)));
  }

  public boolean eligibleMatch(UUID id, Instant now) {
    String sql =
        "SELECT EXISTS(SELECT 1 FROM public.matches m WHERE m.id=:id AND "
            + matchEligibility()
            + " AND EXISTS(SELECT 1 FROM public.markets k JOIN public.market_selections s ON s.market_id=k.id WHERE k.match_id=m.id AND "
            + marketEligibility()
            + " AND "
            + selectionEligibility()
            + "))";
    return jdbc.queryForObject(sql, parameters(now).addValue("id", id), Boolean.class);
  }

  public java.util.Set<UUID> eligibleSelectionIds(java.util.Collection<UUID> ids, Instant now) {
    return java.util.Set.copyOf(
        jdbc.queryForList(
            "SELECT s.id FROM public.market_selections s JOIN public.markets k ON k.id=s.market_id JOIN public.matches m ON m.id=k.match_id WHERE s.id IN (:ids) AND "
                + matchEligibility()
                + " AND "
                + marketEligibility()
                + " AND "
                + selectionEligibility(),
            parameters(now).addValue("ids", ids),
            UUID.class));
  }

  public CatalogDtos.Page<MatchView> matches(
      boolean admin,
      Instant from,
      Instant until,
      String competition,
      MatchStatus status,
      int page,
      int size,
      Instant now) {
    var p = parameters(now).addValue("limit", size).addValue("offset", (long) page * size);
    String where = " WHERE true";
    if (from != null) {
      where += " AND m.match_date>=:from AND m.match_date<:until";
      p.addValue("from", Timestamp.from(from)).addValue("until", Timestamp.from(until));
    }
    if (competition != null && !competition.isBlank()) {
      where += " AND m.competition=:competition";
      p.addValue("competition", competition.strip());
    }
    if (status != null) {
      where += " AND m.status=:status";
      p.addValue("status", status.name());
    }
    if (!admin)
      where +=
          " AND "
              + matchEligibility()
              + " AND EXISTS (SELECT 1 FROM public.markets k JOIN public.market_selections s ON s.market_id=k.id WHERE k.match_id=m.id AND "
              + marketEligibility()
              + " AND "
              + selectionEligibility()
              + ")";
    long total =
        jdbc.queryForObject("SELECT count(*) FROM public.matches m" + where, p, Long.class);
    return CatalogDtos.Page.of(
        jdbc.query(
            "SELECT m.* FROM public.matches m"
                + where
                + " ORDER BY m.match_date,m.id LIMIT :limit OFFSET :offset",
            p,
            this::mapMatch),
        page,
        size,
        total);
  }

  public CatalogDtos.Page<MarketView> markets(
      UUID matchId, boolean admin, int page, int size, Instant now) {
    var p =
        parameters(now)
            .addValue("id", matchId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    String from =
        " FROM public.markets k JOIN public.matches m ON m.id=k.match_id WHERE k.match_id=:id";
    if (!admin)
      from +=
          " AND "
              + matchEligibility()
              + " AND "
              + marketEligibility()
              + " AND EXISTS (SELECT 1 FROM public.market_selections s WHERE s.market_id=k.id AND "
              + selectionEligibility()
              + ")";
    long total = jdbc.queryForObject("SELECT count(*)" + from, p, Long.class);
    return CatalogDtos.Page.of(
        jdbc.query(
            "SELECT k.*, m.version AS match_version"
                + from
                + " ORDER BY k.display_name,k.id LIMIT :limit OFFSET :offset",
            p,
            this::mapMarket),
        page,
        size,
        total);
  }

  public CatalogDtos.Page<SelectionView> selections(
      UUID marketId, boolean admin, int page, int size, Instant now) {
    var p =
        parameters(now)
            .addValue("id", marketId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    String from =
        " FROM public.market_selections s JOIN public.markets k ON k.id=s.market_id JOIN public.matches m ON m.id=k.match_id WHERE k.id=:id";
    if (!admin)
      from +=
          " AND "
              + matchEligibility()
              + " AND "
              + marketEligibility()
              + " AND "
              + selectionEligibility();
    long total = jdbc.queryForObject("SELECT count(*)" + from, p, Long.class);
    return CatalogDtos.Page.of(
        jdbc.query(
            "SELECT s.*, k.match_id,k.source_namespace,k.version AS market_version,m.version AS match_version"
                + from
                + " ORDER BY s.display_name,s.id LIMIT :limit OFFSET :offset",
            p,
            this::mapSelection),
        page,
        size,
        total);
  }

  private MarketView mapMarket(ResultSet row, int index) throws SQLException {
    return new MarketView(
        row.getObject("id", UUID.class),
        row.getObject("match_id", UUID.class),
        row.getLong("match_version"),
        row.getLong("version"),
        row.getString("market_type"),
        row.getString("display_name"),
        row.getString("period"),
        row.getString("line"),
        row.getString("participant"),
        row.getString("rule_key"),
        row.getString("rule_text"),
        row.getString("specifiers_key"),
        row.getString("source_namespace"),
        row.getString("provider_status"),
        row.getBoolean("admin_suspended"),
        row.getBoolean("curated"),
        instant(row, "manual_reviewed_at"),
        instant(row, "request_attempt_at"),
        instant(row, "last_successful_sync_at"),
        instant(row, "provider_updated_at"));
  }

  private SelectionView mapSelection(ResultSet row, int index) throws SQLException {
    return new SelectionView(
        row.getObject("id", UUID.class),
        row.getObject("match_id", UUID.class),
        row.getObject("market_id", UUID.class),
        row.getLong("match_version"),
        row.getLong("market_version"),
        row.getLong("version"),
        row.getString("selection_code"),
        row.getString("display_name"),
        row.getBigDecimal("odds").toPlainString(),
        row.getString("source_namespace"),
        row.getString("provider_status"),
        row.getBoolean("admin_suspended"),
        row.getBoolean("curated"),
        instant(row, "manual_reviewed_at"),
        instant(row, "request_attempt_at"),
        instant(row, "last_successful_sync_at"),
        instant(row, "provider_updated_at"));
  }

  private MatchView mapMatch(ResultSet row, int index) throws SQLException {
    return new MatchView(
        row.getObject("id", UUID.class),
        row.getLong("version"),
        row.getString("competition"),
        row.getString("home_team"),
        row.getString("away_team"),
        row.getTimestamp("match_date").toInstant(),
        MatchStatus.valueOf(row.getString("status")),
        row.getString("source_namespace"),
        row.getString("provider_status"),
        row.getBoolean("admin_suspended"),
        row.getBoolean("structural_conflict"),
        instant(row, "manual_reviewed_at"),
        instant(row, "request_attempt_at"),
        instant(row, "last_successful_sync_at"),
        instant(row, "provider_updated_at"));
  }

  private Instant instant(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
