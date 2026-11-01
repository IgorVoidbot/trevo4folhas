package com.trevo4folhas.metrics;

import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trevo4folhas.metrics.OperationalMetrics.*;

@Repository
public class MetricsRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public MetricsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OperationalMetrics read(Instant now, String zone, LocalDate from, LocalDate to) {
    ZoneId timeZone = ZoneId.of(zone);
    var parameters = new HashMap<String, Object>();
    parameters.put("now", Timestamp.from(now));
    parameters.put("from", Timestamp.from(from.atStartOfDay(timeZone).toInstant()));
    parameters.put("until", Timestamp.from(to.plusDays(1).atStartOfDay(timeZone).toInstant()));
    // Audit timestamps are UTC without timezone; never apply the JVM's default zone.
    Cohort cohort =
        jdbc.queryForObject(
            """
   SELECT count(*) AS emitted,
    count(*) FILTER(WHERE status='PENDING' AND expires_at>:now) AS pending,
    count(*) FILTER(WHERE status='EXPIRED' OR (status='PENDING' AND expires_at<=:now)) AS expired,
    count(*) FILTER(WHERE status='CANCELLED') AS cancelled,
    count(*) FILTER(WHERE status='CONFIRMED') AS confirmed,
    count(*) FILTER(WHERE whatsapp_clicked_at IS NOT NULL AND whatsapp_clicked_at<=:now) AS clicks,
    max(expires_at) AS closes_at
   FROM public.tickets WHERE created_at>=(CAST(:from AS timestamptz) AT TIME ZONE 'UTC') AND created_at<(CAST(:until AS timestamptz) AT TIME ZONE 'UTC')
   """,
            parameters,
            (rs, n) -> {
              long emitted = rs.getLong("emitted"),
                  confirmed = rs.getLong("confirmed"),
                  clicks = rs.getLong("clicks"),
                  expired = rs.getLong("expired");
              Instant closes =
                  rs.getTimestamp("closes_at") == null
                      ? null
                      : rs.getTimestamp("closes_at").toInstant();
              boolean closed = closes == null || !closes.isAfter(now);
              return new Cohort(
                  emitted,
                  rs.getLong("pending"),
                  expired,
                  rs.getLong("cancelled"),
                  confirmed,
                  clicks,
                  closed,
                  closes,
                  percent(clicks, emitted, closed),
                  percent(confirmed, emitted, closed),
                  percent(expired, emitted, closed));
            });
    Confirmed confirmed =
        jdbc.queryForObject(
            """
   WITH decisions AS (SELECT t.*,EXTRACT(EPOCH FROM (handled_at-(created_at AT TIME ZONE 'UTC'))) AS seconds,
    EXISTS(SELECT 1 FROM public.ticket_items i JOIN public.matches m ON m.id=i.match_id WHERE i.ticket_id=t.id AND m.status='CANCELLED') AS affected
    FROM public.tickets t WHERE status='CONFIRMED' AND handled_at>=:from AND handled_at<:until AND handled_at<=:now)
   SELECT count(*) AS count,coalesce(sum(stake_amount),0) AS volume,coalesce(sum(potential_return),0) AS returns,
    count(*) FILTER(WHERE affected) AS affected,count(*) FILTER(WHERE seconds>=0) AS sample,
    percentile_cont(0.5) WITHIN GROUP(ORDER BY seconds) FILTER(WHERE seconds>=0) AS median,
    percentile_cont(0.9) WITHIN GROUP(ORDER BY seconds) FILTER(WHERE seconds>=0) AS p90,
    count(*) FILTER(WHERE seconds<0) AS invalid FROM decisions
   """,
            parameters,
            (rs, n) -> {
              long count = rs.getLong("count");
              BigDecimal volume = rs.getBigDecimal("volume");
              return new Confirmed(
                  count,
                  decimal(volume, 2),
                  count == 0
                      ? null
                      : decimal(
                          volume.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP), 2),
                  decimal(rs.getBigDecimal("returns"), 2),
                  rs.getLong("affected"),
                  rs.getLong("sample"),
                  decimal(rs.getBigDecimal("median"), 3),
                  decimal(rs.getBigDecimal("p90"), 3),
                  rs.getLong("invalid"));
            });
    return new OperationalMetrics(now, zone, from, to, cohort, confirmed);
  }

  private static String percent(long value, long base, boolean closed) {
    return !closed || base == 0
        ? null
        : BigDecimal.valueOf(value)
            .multiply(new BigDecimal("100"))
            .divide(BigDecimal.valueOf(base), 2, RoundingMode.HALF_UP)
            .toPlainString();
  }

  private static String decimal(BigDecimal value, int scale) {
    return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
  }
}
