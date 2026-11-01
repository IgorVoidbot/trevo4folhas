package com.trevo4folhas.metrics;

import java.time.*;

public record OperationalMetrics(
    Instant asOf,
    String bankTimeZone,
    LocalDate from,
    LocalDate to,
    Cohort cohort,
    Confirmed confirmed) {
  public record Cohort(
      long emitted,
      long validPending,
      long effectiveExpired,
      long cancelled,
      long confirmed,
      long whatsappClicks,
      boolean closed,
      Instant closesAt,
      String clickConversionPercent,
      String confirmationConversionPercent,
      String expirationPercent) {}

  public record Confirmed(
      long count,
      String volume,
      String averageStake,
      String potentialReturns,
      long affectedByCancelledMatch,
      long timingSampleSize,
      String medianSeconds,
      String p90Seconds,
      long invalidTimingRecords) {}
}
