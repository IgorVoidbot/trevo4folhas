package com.trevo4folhas.catalog;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trevo4folhas.catalog.CatalogDtos.*;
import com.trevo4folhas.catalog.CatalogRequests.*;
import com.trevo4folhas.match.MatchStatus;

@Service
@Transactional(readOnly = true)
public class CatalogService {
  private final CatalogRepository repository;
  private final Clock clock;
  private final CatalogProperties settings;
  private final ObjectMapper mapper;

  public CatalogService(
      CatalogRepository repository, Clock clock, CatalogProperties settings, ObjectMapper mapper) {
    this.repository = repository;
    this.clock = clock;
    this.settings = settings;
    this.mapper = mapper;
  }

  @Transactional(timeout = 5)
  public MatchView createMatch(CreateMatch request) {
    validateTeams(request.homeTeam(), request.awayTeam(), request.startsAt());
    UUID id = UUID.randomUUID();
    repository.insertMatch(id, request, clock.instant());
    return repository.match(id, false);
  }

  @Transactional(timeout = 5)
  public MarketView createMarket(UUID matchId, CreateMarket request) {
    repository.beginWrite();
    MatchView match = repository.match(matchId, true);
    version(match.matchVersion(), request.matchVersion());
    writable(match);
    manual(match.sourceNamespace());
    String line = canonicalLine(request.line());
    String participant = absent(request.participant());
    String specifiers = canonicalSpecifiers(request.specifiers());
    UUID id = UUID.randomUUID();
    repository.insertMarket(id, matchId, request, line, participant, specifiers, clock.instant());
    return repository.market(id, false);
  }

  @Transactional(timeout = 5)
  public SelectionView createSelection(UUID marketId, CreateSelection request) {
    repository.beginWrite();
    MarketView before = repository.market(marketId, false);
    MatchView match = repository.match(before.matchId(), true);
    MarketView market = repository.market(marketId, true);
    version(match.matchVersion(), request.matchVersion());
    version(market.marketVersion(), request.marketVersion());
    writable(match);
    manual(market.sourceNamespace());
    if (market.adminSuspended() || !"OPEN".equals(market.providerStatus()))
      conflict("O mercado está indisponível.");
    boolean reserved = Set.of("HOME", "DRAW", "AWAY").contains(request.selectionCode());
    if (reserved != "1X2".equals(market.marketType())) {
      throw new CatalogException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SELECTION_CODE",
          "Confira o código da seleção e o tipo de mercado.");
    }
    UUID id = UUID.randomUUID();
    repository.insertSelection(id, marketId, request, clock.instant());
    return repository.selection(id, false);
  }

  @Transactional(timeout = 5)
  public MatchView updateMatch(UUID id, UpdateMatch request) {
    repository.beginWrite();
    MatchView match = repository.match(id, true);
    version(match.matchVersion(), request.matchVersion());
    if (match.status() == MatchStatus.CANCELLED)
      conflict("Partida cancelada não pode ser alterada. Cadastre outra partida.");
    boolean structural =
        !match.competition().equals(request.competition().strip())
            || !match.homeTeam().equals(request.homeTeam().strip())
            || !match.awayTeam().equals(request.awayTeam().strip())
            || !match.startsAt().equals(request.startsAt());
    if (structural) {
      manual(match.sourceNamespace());
      if (repository.matchReferenced(id))
        conflict("Partida vinculada a bilhete não pode ter seus dados alterados.");
      validateTeams(request.homeTeam(), request.awayTeam(), request.startsAt());
    }
    if (request.status() == MatchStatus.OPEN) {
      if (!request.startsAt().isAfter(clock.instant())
          || match.structuralConflict()
          || !"OPEN".equals(match.providerStatus()))
        conflict("A partida não pode ser reaberta nas condições atuais.");
    }
    repository.updateMatch(id, request, clock.instant(), "MANUAL".equals(match.sourceNamespace()));
    return repository.match(id, false);
  }

  @Transactional(timeout = 5)
  public MarketView updateMarket(UUID id, UpdateMarket request) {
    repository.beginWrite();
    MarketView before = repository.market(id, false);
    MatchView match = repository.match(before.matchId(), true);
    MarketView market = repository.market(id, true);
    version(match.matchVersion(), request.matchVersion());
    version(market.marketVersion(), request.marketVersion());
    if (match.status() == MatchStatus.CANCELLED)
      conflict("Partida cancelada não pode ser alterada.");
    boolean changed =
        !market.displayName().equals(request.displayName().strip())
            || !java.util.Objects.equals(market.ruleText(), request.ruleText().strip());
    if (changed) {
      manual(market.sourceNamespace());
      if (repository.marketReferenced(id))
        conflict("Mercado vinculado a bilhete exige uma nova oferta para alterar os termos.");
    }
    if (request.curated() && (market.ruleText() == null || request.ruleText().isBlank()))
      conflict("Informe a regra antes de aprovar o mercado.");
    repository.updateMarket(
        id, request, clock.instant(), "MANUAL".equals(market.sourceNamespace()));
    return repository.market(id, false);
  }

  @Transactional(timeout = 5)
  public SelectionView updateSelection(UUID id, UpdateSelection request) {
    repository.beginWrite();
    SelectionView before = repository.selection(id, false);
    MatchView match = repository.match(before.matchId(), true);
    MarketView market = repository.market(before.marketId(), true);
    SelectionView selection = repository.selection(id, true);
    version(match.matchVersion(), request.matchVersion());
    version(market.marketVersion(), request.marketVersion());
    version(selection.selectionVersion(), request.selectionVersion());
    if (match.status() == MatchStatus.CANCELLED)
      conflict("Partida cancelada não pode ser alterada.");
    boolean labelChanged = !selection.displayName().equals(request.displayName().strip());
    boolean priceChanged = new BigDecimal(selection.odds()).compareTo(request.odds()) != 0;
    if (labelChanged || priceChanged) manual(selection.sourceNamespace());
    if (priceChanged) writable(match);
    if (labelChanged && repository.selectionReferenced(id))
      conflict("Seleção vinculada a bilhete exige uma nova oferta para alterar os termos.");
    repository.updateSelection(
        id, request, clock.instant(), "MANUAL".equals(selection.sourceNamespace()));
    return repository.selection(id, false);
  }

  public Page<MatchView> matches(
      boolean admin, LocalDate date, String competition, MatchStatus status, int page, int size) {
    pagination(page, size);
    if (competition != null && competition.length() > 120) throw new IllegalArgumentException();
    Instant from =
        date == null ? null : date.atStartOfDay(ZoneId.of(settings.bankTimeZone())).toInstant();
    Instant until =
        date == null
            ? null
            : date.plusDays(1).atStartOfDay(ZoneId.of(settings.bankTimeZone())).toInstant();
    return repository.matches(admin, from, until, competition, status, page, size, clock.instant());
  }

  public MatchView match(UUID id, boolean admin) {
    MatchView result = repository.match(id, false);
    if (!admin && !repository.eligibleMatch(id, clock.instant())) unavailable();
    return result;
  }

  public Page<MarketView> markets(UUID matchId, boolean admin, int page, int size) {
    pagination(page, size);
    match(matchId, admin);
    return repository.markets(matchId, admin, page, size, clock.instant());
  }

  public Page<SelectionView> selections(UUID marketId, boolean admin, int page, int size) {
    pagination(page, size);
    repository.market(marketId, false);
    return repository.selections(marketId, admin, page, size, clock.instant());
  }

  private void validateTeams(String home, String away, Instant startsAt) {
    if (home.strip().equalsIgnoreCase(away.strip()))
      throw new CatalogException(
          HttpStatus.BAD_REQUEST, "INVALID_TEAMS", "Mandante e visitante devem ser diferentes.");
    if (!startsAt.isAfter(clock.instant()))
      throw new CatalogException(
          HttpStatus.BAD_REQUEST, "INVALID_KICKOFF", "O início da partida deve estar no futuro.");
  }

  private void writable(MatchView match) {
    if (match.status() != MatchStatus.OPEN
        || match.adminSuspended()
        || match.structuralConflict()
        || !"OPEN".equals(match.providerStatus())
        || !match.startsAt().isAfter(clock.instant()))
      conflict("A partida não está disponível para esta operação.");
  }

  public static void version(long actual, long expected) {
    if (actual != expected) conflict("O catálogo mudou. Atualize os dados antes de continuar.");
  }

  private static void manual(String namespace) {
    if (!"MANUAL".equals(namespace))
      conflict("Dados do fornecedor não podem ser editados como oferta manual.");
  }

  private static void conflict(String message) {
    throw new CatalogException(HttpStatus.CONFLICT, "CATALOG_CONFLICT", message);
  }

  private static void unavailable() {
    throw new CatalogException(HttpStatus.NOT_FOUND, "MATCH_UNAVAILABLE", "Partida indisponível.");
  }

  private static void pagination(int page, int size) {
    if (page < 0 || page > 1000000 || size < 1 || size > 100) throw new IllegalArgumentException();
  }

  public static String absent(String value) {
    return value == null || value.isBlank() ? "NONE" : value.strip();
  }

  public static String canonicalLine(String value) {
    if ("NONE".equals(absent(value))) return "NONE";
    if (!value.matches("-?[0-9]{1,32}(\\.[0-9]{1,30})?")) throw new IllegalArgumentException();
    return new BigDecimal(value).stripTrailingZeros().toPlainString();
  }

  private String canonicalSpecifiers(Map<String, String> values) {
    if (values == null || values.isEmpty()) return "NONE";
    try {
      String result = mapper.writeValueAsString(new TreeMap<>(values));
      if (result.getBytes(StandardCharsets.UTF_8).length > 1024)
        throw new IllegalArgumentException();
      return result;
    } catch (JsonProcessingException invalid) {
      throw new IllegalArgumentException();
    }
  }
}
