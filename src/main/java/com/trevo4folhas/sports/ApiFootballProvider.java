package com.trevo4folhas.sports;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ApiFootballProvider implements SportsDataProvider {
  private final SportsProperties properties;
  private final QuotaStore quota;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final HttpClient client;

  @org.springframework.beans.factory.annotation.Autowired
  public ApiFootballProvider(
      SportsProperties properties, QuotaStore quota, ObjectMapper mapper, Clock clock) {
    this(
        properties,
        quota,
        mapper,
        clock,
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofMillis(Math.max(1, properties.connectTimeoutMs())))
            .build());
  }

  ApiFootballProvider(
      SportsProperties properties,
      QuotaStore quota,
      ObjectMapper mapper,
      Clock clock,
      HttpClient client) {
    this.client = client;
    this.properties = properties;
    this.quota = quota;
    this.mapper = mapper;
    this.clock = clock;
  }

  static URI uri(Endpoint endpoint, Map<String, Integer> parameters) {
    Set<String> allowed =
        endpoint == Endpoint.FIXTURES
            ? Set.of("league", "season")
            : Set.of("league", "season", "bookmaker", "bet", "page");
    if (!allowed.containsAll(parameters.keySet())
        || parameters.values().stream().anyMatch(v -> v == null || v <= 0))
      throw new SyncFailure("INVALID_PARAMETERS");
    String query =
        new TreeMap<>(parameters)
            .entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    return URI.create(
        "https://v3.football.api-sports.io/"
            + (endpoint == Endpoint.FIXTURES ? "fixtures" : "odds")
            + "?"
            + query);
  }

  @Override
  public Page fetch(Endpoint endpoint, Map<String, Integer> parameters, Instant deadline) {
    if (!properties.configured() || TransactionSynchronizationManager.isActualTransactionActive())
      throw new SyncFailure("CONFIGURATION_BLOCKED");
    if (!properties.leagueAllowlist().contains(parameters.get("league"))
        || !properties.seasonAllowlist().contains(parameters.get("season"))
        || (endpoint == Endpoint.ODDS
            && (!properties.bookmakerAllowlist().contains(parameters.get("bookmaker"))
                || !properties.marketAllowlist().contains(parameters.get("bet")))))
      throw new SyncFailure("ALLOWLIST_BLOCKED");
    for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
      long remaining = Duration.between(clock.instant(), deadline).toMillis();
      if (remaining <= 0) throw new SyncFailure("RUN_TIMEOUT");
      quota.reserve();
      HttpRequest request =
          HttpRequest.newBuilder(uri(endpoint, parameters))
              .timeout(Duration.ofMillis(Math.min(remaining, properties.readTimeoutMs())))
              .header("x-apisports-key", properties.apiKey())
              .GET()
              .build();
      CompletableFuture<HttpResponse<byte[]>> future =
          client.sendAsync(request, info -> new LimitedBody());
      try {
        HttpResponse<byte[]> response =
            future.get(Math.min(remaining, properties.readTimeoutMs()), TimeUnit.MILLISECONDS);
        int daily = header(response, "x-ratelimit-requests-remaining");
        int minute = header(response, "x-ratelimit-remaining");
        Instant retryAt =
            response.statusCode() == 429
                ? retryAt(response.headers().firstValue("retry-after").orElse(null))
                : null;
        quota.observe(daily, minute < 0 ? 0 : minute, retryAt);
        if (response.statusCode() == 429
            || response.statusCode() == 499
            || response.statusCode() >= 500) {
          if (attempt == properties.maxRetries())
            throw new SyncFailure(
                response.statusCode() == 429 ? "PROVIDER_RATE_LIMIT" : "PROVIDER_UNAVAILABLE");
          pause(attempt, retryAt, deadline);
          continue;
        }
        if (response.statusCode() != 200) throw new SyncFailure("PROVIDER_HTTP_ERROR");
        JsonNode root = mapper.readTree(response.body());
        if (root == null
            || !root.has("errors")
            || !root.get("errors").isContainerNode()
            || !root.get("errors").isEmpty()) throw new SyncFailure("PROVIDER_BODY_ERROR");
        JsonNode rows = root.path("response");
        int current = root.path("paging").path("current").asInt(-1);
        int total = root.path("paging").path("total").asInt(-1);
        if (!rows.isArray()
            || total < 1
            || current != parameters.getOrDefault("page", 1)
            || current > total
            || root.path("results").asInt(-1) != rows.size()
            || (endpoint == Endpoint.FIXTURES && total != 1))
          throw new SyncFailure("INVALID_PROVIDER_PAGE");
        return new Page(rows, total, daily, minute);
      } catch (TimeoutException | ExecutionException failure) {
        future.cancel(true);
        if (attempt == properties.maxRetries()) throw new SyncFailure("PROVIDER_TIMEOUT");
        pause(attempt, null, deadline);
      } catch (InterruptedException failure) {
        future.cancel(true);
        Thread.currentThread().interrupt();
        throw new SyncFailure("RUN_INTERRUPTED");
      } catch (java.io.IOException failure) {
        throw new SyncFailure("INVALID_PROVIDER_PAYLOAD");
      }
    }
    throw new SyncFailure("PROVIDER_UNAVAILABLE");
  }

  private int header(HttpResponse<?> response, String name) {
    try {
      return Math.max(-1, Integer.parseInt(response.headers().firstValue(name).orElse("-1")));
    } catch (NumberFormatException invalid) {
      return -1;
    }
  }

  private Instant retryAt(String value) {
    try {
      return clock.instant().plusSeconds(Math.max(1, Long.parseLong(value)));
    } catch (RuntimeException invalid) {
      try {
        return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
      } catch (RuntimeException absent) {
        return clock.instant().plusSeconds(60);
      }
    }
  }

  private void pause(int attempt, Instant retryAt, Instant deadline) {
    long delay =
        Math.max(
            500L * (1L << attempt) + ThreadLocalRandom.current().nextLong(250),
            retryAt == null ? 0 : Duration.between(clock.instant(), retryAt).toMillis());
    if (delay > 10000 || !clock.instant().plusMillis(delay).isBefore(deadline))
      throw new SyncFailure("RATE_LIMIT_WAIT");
    try {
      Thread.sleep(delay);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new SyncFailure("RUN_INTERRUPTED");
    }
  }

  static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    @Override
    public CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        if (bytes.size() + buffer.remaining() > 2 * 1024 * 1024) {
          subscription.cancel();
          result.completeExceptionally(new SyncFailure("PAYLOAD_TOO_LARGE"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
      result.completeExceptionally(new SyncFailure("TRANSPORT_ERROR"));
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
