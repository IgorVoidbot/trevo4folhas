package com.trevo4folhas.ticket;

import java.time.*;
import java.util.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trevo4folhas.catalog.*;
import com.trevo4folhas.ticket.TicketOperationsDtos.*;

@Service
public class TicketOperationsService {
  private final TicketOperationsRepository repository;
  private final TicketStore store;
  private final TicketSummary summary;
  private final CatalogRepository catalog;
  private final CatalogProperties settings;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public TicketOperationsService(
      TicketOperationsRepository repository,
      TicketStore store,
      TicketSummary summary,
      CatalogRepository catalog,
      CatalogProperties settings,
      Clock clock,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.store = store;
    this.summary = summary;
    this.catalog = catalog;
    this.settings = settings;
    this.clock = clock;
    this.events = events;
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public TicketQueue list(
      UUID actor,
      String code,
      TicketStatus status,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    repository.requireActive(actor, false);
    if ((from != null && (from.getYear() < 1900 || from.getYear() > 2200))
        || (to != null && (to.getYear() < 1900 || to.getYear() > 2200))
        || page < 0
        || page > 100000
        || size < 1
        || size > 100
        || (from != null && to != null && from.isAfter(to)))
      throw TicketException.invalid("Confira o período e a paginação informados.");
    if (code != null) {
      code = code.strip().toUpperCase(Locale.ROOT);
      if (code.isEmpty()) code = null;
      else if (code.length() > 32 || code.codePoints().anyMatch(Character::isISOControl))
        throw TicketException.invalid("Confira o código informado.");
    }
    ZoneId zone = ZoneId.of(settings.bankTimeZone());
    return repository.list(
        code,
        status,
        from == null ? null : from.atStartOfDay(zone).toInstant(),
        to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
        page,
        size,
        clock.instant(),
        zone.getId());
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Detail detail(UUID id, UUID actor) {
    repository.requireActive(actor, false);
    return detail(repository.header(id, false));
  }

  private Detail detail(TicketOperationsRepository.Header h) {
    var ticket = store.find(h.requestKey(), false);
    return new Detail(
        summary.receipt(ticket, store.snapshots(h.id())),
        h.version(),
        h.handledBy(),
        h.handledByName(),
        h.handledAt(),
        h.reason(),
        h.note(),
        h.affected());
  }

  @Transactional(timeout = 5)
  public Decision confirm(UUID id, UUID actor, TicketOperationsRequests.Confirm request) {
    if (!request.pixReceiptChecked())
      throw TicketException.invalid("Declare a conferência do Pix antes de confirmar.");
    return decide(
        id, actor, request.ticketVersion(), null, cleanNote(request.operatorNote()), true);
  }

  @Transactional(timeout = 5)
  public Decision cancel(UUID id, UUID actor, TicketOperationsRequests.Cancel request) {
    String reason = request.reason().strip();
    if (reason.length() < 2
        || reason.length() > 250
        || reason.codePoints().anyMatch(Character::isISOControl))
      throw TicketException.invalid(
          "Informe um motivo entre 2 e 250 caracteres, sem quebras de linha.");
    return decide(
        id, actor, request.ticketVersion(), reason, cleanNote(request.operatorNote()), false);
  }

  private String cleanNote(String note) {
    if (note == null || note.isBlank()) return null;
    note = note.strip();
    if (note.length() > 500
        || note.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
      throw TicketException.invalid("Confira a observação, limitada a 500 caracteres.");
    return note;
  }

  private Decision decide(
      UUID id, UUID actor, long expected, String reason, String note, boolean confirm) {
    repository.beginWrite();
    var header = repository.header(id, true);
    repository.requireActive(actor, true);
    Instant now = clock.instant();
    if (header.status() == TicketStatus.PENDING && !now.isBefore(header.expiresAt()))
      return expired(id, actor, now);
    if (header.status() != TicketStatus.PENDING || header.version() != expected)
      return conflict(
          id,
          actor,
          "TICKET_CHANGED",
          "O pré-bilhete mudou ou já foi decidido. Confira o estado atual antes de continuar.");
    if (confirm) {
      var ids = repository.selectionIds(id);
      if (ids.isEmpty())
        return conflict(
            id,
            actor,
            "OFFER_UNAVAILABLE",
            "O pré-bilhete não contém ofertas válidas. Consulte a banca.");
      var offers = store.offers(ids);
      if (offers.size() != ids.size())
        return conflict(
            id, actor, "OFFER_UNAVAILABLE", "Uma oferta está indisponível. Confira o catálogo.");
      store.lockCatalog(offers);
      now = clock.instant();
      if (!now.isBefore(header.expiresAt())) return expired(id, actor, now);
      if (catalog.eligibleSelectionIds(ids, now).size() != ids.size())
        return conflict(
            id,
            actor,
            "OFFER_UNAVAILABLE",
            "Uma partida ou oferta está suspensa, vencida ou indisponível. Confira o catálogo antes de confirmar.");
    }
    now = clock.instant();
    if (!now.isBefore(header.expiresAt())) return expired(id, actor, now);
    if (repository.decide(
            id,
            expected,
            confirm ? TicketStatus.CONFIRMED : TicketStatus.CANCELLED,
            actor,
            reason,
            note,
            now)
        != 1)
      return conflict(id, actor, "TICKET_CHANGED", "O pré-bilhete mudou. Confira o estado atual.");
    var result = detail(repository.header(id, false));
    events.publishEvent(
        new TicketOperationAudit.Event(
            id,
            actor,
            confirm ? "CONFIRM" : "CANCEL",
            result.ticket().status(),
            result.ticketVersion()));
    return new Decision(true, null, "Decisão registrada.", result);
  }

  private Decision expired(UUID id, UUID actor, Instant now) {
    if (repository.expire(id, now) > 0)
      events.publishEvent(
          new TicketOperationAudit.Event(
              id, null, "EXPIRE", TicketStatus.EXPIRED, repository.header(id, false).version()));
    return conflict(
        id,
        actor,
        "TICKET_EXPIRED",
        "O prazo encerrou. Não é possível confirmar ou cancelar. Pagamento recebido após o vencimento exige atendimento manual fora do sistema.");
  }

  private Decision conflict(UUID id, UUID actor, String code, String message) {
    var current = detail(repository.header(id, false));
    events.publishEvent(
        new TicketOperationAudit.Event(
            id, actor, "CONFLICT", current.ticket().status(), current.ticketVersion()));
    return new Decision(false, code, message, current);
  }
}
