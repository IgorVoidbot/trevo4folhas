package com.trevo4folhas.metrics;

import java.time.*;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import com.trevo4folhas.catalog.CatalogProperties;
import com.trevo4folhas.ticket.*;

@Service
public class MetricsService {
  private final MetricsRepository repository;
  private final TicketOperationsRepository operators;
  private final CatalogProperties settings;
  private final Clock clock;

  public MetricsService(
      MetricsRepository repository,
      TicketOperationsRepository operators,
      CatalogProperties settings,
      Clock clock) {
    this.repository = repository;
    this.operators = operators;
    this.settings = settings;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OperationalMetrics read(UUID actor, LocalDate from, LocalDate to) {
    operators.requireActive(actor, false);
    Instant now = clock.instant();
    LocalDate today = now.atZone(ZoneId.of(settings.bankTimeZone())).toLocalDate();
    from = from == null ? today : from;
    to = to == null ? today : to;
    if (from.getYear() < 1900 || to.getYear() > 2200 || from.isAfter(to))
      throw TicketException.invalid("Confira o período informado.");
    return repository.read(now, settings.bankTimeZone(), from, to);
  }
}
