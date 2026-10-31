package com.trevo4folhas.ticket;

import java.time.*;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trevo4folhas.catalog.CatalogRepository;
import com.trevo4folhas.config.BettingProperties;

@Service
public class TicketWriter {
  public record Result(TicketReceipt receipt, boolean created) {}

  private final TicketStore store;
  private final CatalogRepository catalog;
  private final BettingProperties limits;
  private final MoneyCalculator calculator;
  private final TicketCodeGenerator codes;
  private final TicketSummary summary;
  private final Clock clock;

  public TicketWriter(
      TicketStore store,
      CatalogRepository catalog,
      BettingProperties limits,
      MoneyCalculator calculator,
      TicketCodeGenerator codes,
      TicketSummary summary,
      Clock clock) {
    this.store = store;
    this.catalog = catalog;
    this.limits = limits;
    this.calculator = calculator;
    this.codes = codes;
    this.summary = summary;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public Result issue(UUID key, String sessionHash, TicketIntent intent) {
    store.coordinate(key);
    var existing = store.find(key, true);
    if (existing != null) return replay(existing, sessionHash, intent.requestHash());
    if (!TicketRules.VERSION.equals(intent.rulesVersion()))
      throw new TicketException(
          HttpStatus.CONFLICT,
          "RULES_CHANGED",
          "As regras foram atualizadas. Recarregue a página e leia as condições antes de emitir.");
    if (intent.items().size() > limits.getMaxSelections())
      throw TicketException.invalid("O cupom excede o limite de seleções.");
    var ids = intent.items().stream().map(TicketRequests.Item::selectionId).toList();
    var before = store.offers(ids);
    if (before.size() != ids.size()) throw TicketException.unavailable();
    if (before.stream().map(TicketStore.Offer::matchId).distinct().count() != before.size())
      throw TicketException.invalid("Escolha somente uma seleção por partida.");
    store.lockCatalog(before);
    var offers = store.offers(ids);
    Instant now = clock.instant();
    if (offers.size() != ids.size() || catalog.eligibleSelectionIds(ids, now).size() != ids.size())
      throw TicketException.unavailable();
    for (var offer : offers) {
      var expected =
          intent.items().stream()
              .filter(item -> item.selectionId().equals(offer.selectionId()))
              .findFirst()
              .orElseThrow();
      if (offer.matchVersion() != expected.matchVersion()
          || offer.marketVersion() != expected.marketVersion()
          || offer.selectionVersion() != expected.selectionVersion())
        throw TicketException.unavailable();
    }
    var calculation =
        calculator.calculate(
            intent.stakeAmount(), offers.stream().map(TicketStore.Offer::odds).toList(), limits);
    Instant expiry = store.creationTime().plusSeconds(limits.getTicketValidityMinutes() * 60L);
    for (var offer : offers) if (offer.startsAt().isBefore(expiry)) expiry = offer.startsAt();
    if (!expiry.isAfter(clock.instant())) throw TicketException.unavailable();
    store.insert(UUID.randomUUID(), key, codes.next(), sessionHash, intent, calculation, expiry);
    var saved = store.find(key, false);
    return new Result(summary.receipt(saved, store.snapshots(saved.id())), true);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public Result recover(UUID key, String sessionHash, String requestHash) {
    store.coordinate(key);
    var saved = store.find(key, true);
    if (saved == null) throw TicketException.conflict();
    return replay(saved, sessionHash, requestHash);
  }

  private Result replay(TicketStore.Stored saved, String sessionHash, String requestHash) {
    if (!TicketIntent.equal(saved.sessionHash(), sessionHash)
        || !TicketIntent.equal(saved.requestHash(), requestHash)) throw TicketException.conflict();
    return new Result(summary.receipt(saved, store.snapshots(saved.id())), false);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public void click(UUID key, String sessionHash) {
    store.coordinate(key);
    var saved = store.find(key, true);
    if (saved == null || !TicketIntent.equal(saved.sessionHash(), sessionHash))
      throw TicketException.conflict();
    store.click(saved.id(), clock.instant());
  }
}
