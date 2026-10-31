package com.trevo4folhas.sports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class ApiFootballProviderTest {
  private final Instant now = Instant.parse("2026-10-31T03:01:00Z");
  private final QuotaStore quota = mock(QuotaStore.class);
  private final HttpClient client = mock(HttpClient.class);
  private final com.fasterxml.jackson.databind.ObjectMapper mapper = SportsTestSupport.mapper();
  private final SportsProperties properties = SportsTestSupport.properties(Path.of("/tmp/unused"));
  private final ApiFootballProvider provider =
      new ApiFootballProvider(properties, quota, mapper, Clock.fixed(now, ZoneOffset.UTC), client);
  private final Map<String, Integer> query =
      Map.of("league", 999, "season", 2030, "bookmaker", 99, "bet", 1, "page", 1);

  @SuppressWarnings("unchecked")
  private void response(int status, String body, Map<String, List<String>> headers) {
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    when(response.headers()).thenReturn(HttpHeaders.of(headers, (a, b) -> true));
    when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(response));
  }

  @Test
  void fixedHostTypedAllowlistedQueriesAndReservationBeforeTransport() throws Exception {
    response(
        200,
        SportsTestSupport.sample("odds", mapper).toString(),
        Map.of(
            "x-ratelimit-requests-remaining", List.of("9"), "x-ratelimit-remaining", List.of("8")));
    assertThat(
            provider
                .fetch(SportsDataProvider.Endpoint.ODDS, query, now.plusSeconds(10))
                .totalPages())
        .isEqualTo(1);
    var order = inOrder(quota, client);
    order.verify(quota).reserve();
    order.verify(client).sendAsync(any(), any());
    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).sendAsync(request.capture(), any());
    assertThat(request.getValue().uri().getHost()).isEqualTo("v3.football.api-sports.io");
    verify(quota).observe(9, 8, null);
    assertThatThrownBy(
            () -> ApiFootballProvider.uri(SportsDataProvider.Endpoint.ODDS, Map.of("host", 1)))
        .isInstanceOf(SyncFailure.class);
    assertThatThrownBy(
            () ->
                provider.fetch(
                    SportsDataProvider.Endpoint.ODDS,
                    Map.of("league", 1, "season", 2030),
                    now.plusSeconds(10)))
        .isInstanceOf(SyncFailure.class);
  }

  @Test
  void http200BodyErrorAndRedirectAreSanitized() {
    response(200, "{\"errors\":{\"token\":\"secret-provider-message\"}}", Map.of());
    assertThatThrownBy(
            () -> provider.fetch(SportsDataProvider.Endpoint.ODDS, query, now.plusSeconds(10)))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("PROVIDER_BODY_ERROR")
        .hasMessageNotContaining("secret-provider-message");
    response(302, "sensitive body", Map.of("location", List.of("http://127.0.0.1")));
    assertThatThrownBy(
            () -> provider.fetch(SportsDataProvider.Endpoint.ODDS, query, now.plusSeconds(10)))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("PROVIDER_HTTP_ERROR");
  }

  @Test
  void rateLimitPersistsRetryAfterAndNeverReturnsComplete() {
    response(429, "{}", Map.of("retry-after", List.of("120")));
    assertThatThrownBy(
            () -> provider.fetch(SportsDataProvider.Endpoint.ODDS, query, now.plusSeconds(10)))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("PROVIDER_RATE_LIMIT");
    verify(quota).observe(-1, 0, now.plusSeconds(120));
  }

  @Test
  @SuppressWarnings("unchecked")
  void stalledBodyTimesOutAndCancelsTransport() {
    CompletableFuture<HttpResponse<byte[]>> stalled = new CompletableFuture<>();
    when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(stalled);
    assertThatThrownBy(
            () -> provider.fetch(SportsDataProvider.Endpoint.ODDS, query, now.plusSeconds(10)))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("PROVIDER_TIMEOUT");
    assertThat(stalled.isCancelled()).isTrue();
  }

  @Test
  void malformedPaginationAndExpiredDeadlineFailClosed() {
    response(
        200,
        "{\"errors\":[],\"results\":0,\"response\":[],\"paging\":{\"current\":2,\"total\":1}}",
        Map.of());
    assertThatThrownBy(
            () -> provider.fetch(SportsDataProvider.Endpoint.ODDS, query, now.plusSeconds(10)))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("INVALID_PROVIDER_PAGE");
    assertThatThrownBy(() -> provider.fetch(SportsDataProvider.Endpoint.ODDS, query, now))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("RUN_TIMEOUT");
  }

  @Test
  void payloadLimitCancelsSubscriptionAndDoesNotRetainOversizedBody() {
    var body = new ApiFootballProvider.LimitedBody();
    var subscription = mock(java.util.concurrent.Flow.Subscription.class);
    body.onSubscribe(subscription);
    body.onNext(List.of(ByteBuffer.allocate(2 * 1024 * 1024 + 1)));
    verify(subscription).cancel();
    assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();
    var small = new ApiFootballProvider.LimitedBody();
    small.onSubscribe(subscription);
    small.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2})));
    small.onComplete();
    assertThat(small.getBody().toCompletableFuture().join()).containsExactly((byte) 1, (byte) 2);
  }
}
