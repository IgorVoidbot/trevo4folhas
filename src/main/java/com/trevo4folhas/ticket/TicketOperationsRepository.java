package com.trevo4folhas.ticket;

import java.sql.*;
import java.time.*;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import com.trevo4folhas.catalog.CatalogDtos.Page;
import com.trevo4folhas.ticket.TicketOperationsDtos.*;

@Repository
public class TicketOperationsRepository {
  record Header(
      UUID id,
      UUID requestKey,
      long version,
      TicketStatus status,
      Instant expiresAt,
      UUID handledBy,
      String handledByName,
      Instant handledAt,
      String reason,
      String note,
      boolean affected) {
    @Override
    public String toString() {
      return "TicketHeader[redacted]";
    }
  }

  private static final String AFFECTED =
      "EXISTS(SELECT 1 FROM public.ticket_items i JOIN public.matches m ON m.id=i.match_id WHERE i.ticket_id=t.id AND m.status='CANCELLED')";
  private final NamedParameterJdbcTemplate jdbc;

  public TicketOperationsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void beginWrite() {
    jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout='2s'");
    jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout='4s'");
  }

  public void requireActive(UUID actor, boolean lock) {
    if (actor == null
        || jdbc.queryForList(
                "SELECT id FROM public.admins WHERE id=:id AND active" + (lock ? " FOR SHARE" : ""),
                Map.of("id", actor),
                UUID.class)
            .isEmpty())
      throw new TicketException(
          HttpStatus.FORBIDDEN,
          "OPERATOR_INACTIVE",
          "A sessão não pertence a um operador ativo. Entre novamente.");
  }

  public Header header(UUID id, boolean lock) {
    var rows =
        jdbc.query(
            "SELECT t.*,a.display_name AS handler_name,"
                + AFFECTED
                + " AS affected FROM public.tickets t LEFT JOIN public.admins a ON a.id=t.handled_by WHERE t.id=:id"
                + (lock ? " FOR UPDATE OF t" : ""),
            Map.of("id", id),
            (rs, n) ->
                new Header(
                    rs.getObject("id", UUID.class),
                    rs.getObject("request_key", UUID.class),
                    rs.getLong("version"),
                    TicketStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getObject("handled_by", UUID.class),
                    rs.getString("handler_name"),
                    instant(rs, "handled_at"),
                    rs.getString("cancellation_reason"),
                    rs.getString("operator_note"),
                    rs.getBoolean("affected")));
    if (rows.isEmpty())
      throw new TicketException(
          HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Pré-bilhete não encontrado.");
    return rows.get(0);
  }

  public List<UUID> selectionIds(UUID ticket) {
    return jdbc.queryForList(
        "SELECT market_selection_id FROM public.ticket_items WHERE ticket_id=:id ORDER BY match_id",
        Map.of("id", ticket),
        UUID.class);
  }

  public TicketQueue list(
      String code,
      TicketStatus status,
      Instant from,
      Instant until,
      int page,
      int size,
      Instant now,
      String zone) {
    var params =
        new MapSqlParameterSource()
            .addValue("now", Timestamp.from(now))
            .addValue("size", size)
            .addValue("offset", (long) page * size);
    String where = " WHERE TRUE";
    if (code != null) {
      where += " AND t.ticket_code=:code";
      params.addValue("code", code);
    }
    if (from != null) {
      where += " AND t.created_at>=:from";
      params.addValue("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC));
    }
    if (until != null) {
      where += " AND t.created_at<:until";
      params.addValue("until", LocalDateTime.ofInstant(until, ZoneOffset.UTC));
    }
    String prefix =
        "WITH selected AS (SELECT t.*,CASE WHEN t.status='PENDING' AND t.expires_at<=:now THEN 'EXPIRED' ELSE t.status END AS effective_status FROM public.tickets t"
            + where
            + ") ";
    String filter = status == null ? "" : " WHERE t.effective_status=:status";
    if (status != null) params.addValue("status", status.name());
    Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
    for (var value : TicketStatus.values()) counts.put(value, 0L);
    jdbc.query(
        prefix
            + "SELECT effective_status,count(*) AS amount FROM selected t"
            + filter
            + " GROUP BY effective_status",
        params,
        rs -> {
          counts.put(TicketStatus.valueOf(rs.getString(1)), rs.getLong(2));
        });
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    var rows =
        jdbc.query(
            prefix
                + "SELECT t.*,"
                + AFFECTED
                + " AS affected FROM selected t"
                + filter
                + " ORDER BY t.created_at DESC,t.id DESC LIMIT :size OFFSET :offset",
            params,
            (rs, n) ->
                new Row(
                    rs.getObject("id", UUID.class),
                    rs.getString("ticket_code"),
                    rs.getString("bettor_name"),
                    rs.getString("bettor_phone"),
                    rs.getBigDecimal("stake_amount").toPlainString(),
                    rs.getBigDecimal("potential_return").toPlainString(),
                    TicketStatus.valueOf(rs.getString("effective_status")),
                    rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getLong("version"),
                    rs.getBoolean("affected")));
    return new TicketQueue(Page.of(rows, page, size, total), counts, zone);
  }

  public int decide(
      UUID id,
      long version,
      TicketStatus status,
      UUID actor,
      String reason,
      String note,
      Instant now) {
    return jdbc.update(
        "UPDATE public.tickets SET status=:status,handled_by=:actor,handled_at=:now,cancellation_reason=:reason,operator_note=:note,version=version+1 WHERE id=:id AND version=:version AND status='PENDING' AND expires_at>:now",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("version", version)
            .addValue("status", status.name())
            .addValue("actor", actor)
            .addValue("now", Timestamp.from(now))
            .addValue("reason", reason)
            .addValue("note", note));
  }

  public int expire(UUID id, Instant now) {
    return jdbc.update(
        "UPDATE public.tickets SET status='EXPIRED',version=version+1 WHERE id=:id AND status='PENDING' AND expires_at<=:now",
        Map.of("id", id, "now", Timestamp.from(now)));
  }

  record Expired(UUID id, long version) {}

  public List<Expired> expireBatch(Instant now) {
    return jdbc.query(
        """
      WITH due AS (SELECT id FROM public.tickets WHERE status='PENDING' AND expires_at<=:now ORDER BY id LIMIT 200 FOR UPDATE SKIP LOCKED)
      UPDATE public.tickets t SET status='EXPIRED',version=t.version+1 FROM due WHERE t.id=due.id AND t.status='PENDING' AND t.expires_at<=:now RETURNING t.id,t.version
      """,
        Map.of("now", Timestamp.from(now)),
        (rs, n) -> new Expired(rs.getObject("id", UUID.class), rs.getLong("version")));
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    return rs.getTimestamp(name) == null ? null : rs.getTimestamp(name).toInstant();
  }
}
