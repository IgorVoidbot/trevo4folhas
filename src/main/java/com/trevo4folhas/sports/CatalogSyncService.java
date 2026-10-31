package com.trevo4folhas.sports;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CatalogSyncService {
  public record Status(
      String source,
      boolean enabled,
      boolean running,
      String outcome,
      String message,
      Instant lastAttemptAt,
      Instant lastSuccessfulSyncAt,
      int dailyBudget,
      int reserve,
      Integer used,
      Integer remaining,
      Instant resetAt,
      Instant notBefore) {}

  private final SportsProperties properties;
  private final SportsDataProvider provider;
  private final ProviderMapping mapping;
  private final ImportService importer;
  private final QuotaStore quota;
  private final Clock clock;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile String lastOutcome = "NOT_RUN";

  public CatalogSyncService(
      SportsProperties properties,
      SportsDataProvider provider,
      ProviderMapping mapping,
      ImportService importer,
      QuotaStore quota,
      Clock clock) {
    this.properties = properties;
    this.provider = provider;
    this.mapping = mapping;
    this.importer = importer;
    this.quota = quota;
    this.clock = clock;
  }

  public Status status() {
    QuotaStore.State state = null;
    String outcome = lastOutcome;
    if (!properties.enabled()) outcome = "DISABLED";
    else if (!properties.configured()) outcome = "CONFIGURATION_BLOCKED";
    else {
      try {
        state = quota.read();
        outcome = state.outcome() == null ? "NOT_RUN" : state.outcome();
        if (!state.windowEndsAt().isAfter(clock.instant())) outcome = "QUOTA_RESET_UNVERIFIED";
      } catch (SyncFailure blocked) {
        outcome = blocked.code();
      }
    }
    String message = message(outcome);
    return new Status(
        "API_FOOTBALL",
        properties.configured() && state != null && state.windowEndsAt().isAfter(clock.instant()),
        running.get(),
        safeCode(outcome),
        message,
        state == null ? null : state.lastAttemptAt(),
        state == null ? null : state.lastSuccessAt(),
        properties.dailyRequestBudget(),
        properties.requestReserve(),
        state == null ? null : state.used(),
        state == null
            ? null
            : Math.max(
                0,
                Math.min(properties.dailyRequestBudget() - state.used(), state.providerRemaining())
                    - properties.requestReserve()),
        state == null ? null : state.windowEndsAt(),
        state == null ? null : state.notBefore());
  }

  public Status run() {
    if (!properties.configured()) {
      lastOutcome = "CONFIGURATION_BLOCKED";
      return status();
    }
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new SyncFailure("TRANSACTION_ACTIVE");
    if (!running.compareAndSet(false, true)) throw new SyncFailure("RUN_BUSY");
    try (QuotaStore.RunLock ignored = quota.acquireRun()) {
      Instant deadline = clock.instant().plusSeconds(properties.maxRunSeconds());
      int pages = 0;
      boolean partial = false;
      try {
        for (int league : new TreeSet<>(properties.leagueAllowlist()))
          for (int season : new TreeSet<>(properties.seasonAllowlist())) {
            check(deadline, ++pages);
            var fixturePage =
                provider.fetch(
                    SportsDataProvider.Endpoint.FIXTURES,
                    Map.of("league", league, "season", season),
                    deadline);
            if (fixturePage.totalPages() != 1) throw new SyncFailure("PAGE_LIMIT");
            for (var fixture : mapping.fixtures(fixturePage.rows())) {
              checkTime(deadline);
              importer.fixture(fixture);
            }
            for (int bookmaker : new TreeSet<>(properties.bookmakerAllowlist()))
              for (int market : new TreeSet<>(properties.marketAllowlist())) {
                int total = 1;
                for (int page = 1; page <= total; page++) {
                  check(deadline, ++pages);
                  var result =
                      provider.fetch(
                          SportsDataProvider.Endpoint.ODDS,
                          Map.of(
                              "league",
                              league,
                              "season",
                              season,
                              "bookmaker",
                              bookmaker,
                              "bet",
                              market,
                              "page",
                              page),
                          deadline);
                  if (page > 1 && result.totalPages() != total)
                    throw new SyncFailure("PAGINATION_CHANGED");
                  total = result.totalPages();
                  for (var offer : mapping.offers(result.rows())) {
                    checkTime(deadline);
                    if (!importer.offer(offer)) partial = true;
                  }
                }
              }
          }
        lastOutcome = partial ? "PARTIAL" : "COMPLETE";
        quota.finish(lastOutcome, !partial);
      } catch (SyncFailure failure) {
        lastOutcome = failure.code();
        quota.finish(lastOutcome, false);
      } catch (RuntimeException failure) {
        lastOutcome = "IMPORT_ERROR";
        quota.finish(lastOutcome, false);
      }
    } catch (java.io.IOException failure) {
      lastOutcome = "QUOTA_STATE_UNAVAILABLE";
    } finally {
      running.set(false);
    }
    return status();
  }

  private void check(Instant deadline, int pages) {
    checkTime(deadline);
    if (pages > properties.maxPages()) throw new SyncFailure("PAGE_LIMIT");
  }

  private void checkTime(Instant deadline) {
    if (!clock.instant().isBefore(deadline)) throw new SyncFailure("RUN_TIMEOUT");
  }

  private static final Map<String, String> MESSAGES =
      Map.ofEntries(
          Map.entry("DISABLED", "Integração desativada. Nenhuma chamada externa será realizada."),
          Map.entry(
              "CONFIGURATION_BLOCKED",
              "Integração bloqueada: confira credencial, mapeamentos, listas permitidas e política experimental de desenvolvimento."),
          Map.entry(
              "COMPLETE",
              "Última execução concluída. As ofertas ainda dependem de curadoria e atualização; isto não comprova paridade do fornecedor."),
          Map.entry(
              "PARTIAL",
              "Importação parcial: ofertas inválidas foram bloqueadas. Revise o catálogo."),
          Map.entry("NOT_RUN", "Integração configurada, ainda sem execução."),
          Map.entry(
              "RUNNING",
              "Execução iniciada. Se o processo foi interrompido, confira o catálogo antes de uma nova execução."),
          Map.entry("QUOTA_EXHAUSTED", "Orçamento de chamadas esgotado; a reserva foi preservada."),
          Map.entry(
              "QUOTA_RESET_UNVERIFIED",
              "Janela de quota encerrada. Confirme o reset com o fornecedor antes de renovar o estado."),
          Map.entry(
              "QUOTA_STATE_UNAVAILABLE",
              "Estado durável de quota ausente, inválido ou sem proteção. Chamadas bloqueadas."),
          Map.entry(
              "RATE_LIMIT_WAIT",
              "Aguarde o prazo de limite do fornecedor antes de tentar novamente."),
          Map.entry(
              "PROVIDER_RATE_LIMIT", "Fornecedor limitou as chamadas. Importação incompleta."),
          Map.entry(
              "PAGE_LIMIT",
              "Limite de páginas atingido. Importação incompleta; registros ausentes foram preservados."),
          Map.entry("RUN_TIMEOUT", "Tempo máximo atingido. Importação incompleta."),
          Map.entry("UNMAPPED_MARKET", "Mercado sem mapeamento aprovado. Importação incompleta."),
          Map.entry(
              "UNMAPPED_SELECTION", "Seleção sem mapeamento aprovado. Importação incompleta."),
          Map.entry("RUN_BUSY", "Já existe uma sincronização em andamento."));

  private static String safeCode(String code) {
    return MESSAGES.containsKey(code) ? code : "IMPORT_ERROR";
  }

  private static String message(String code) {
    return MESSAGES.getOrDefault(
        code,
        "A importação não foi concluída. Confira a configuração e o fornecedor. O catálogo anterior foi preservado.");
  }
}
