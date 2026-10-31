package com.trevo4folhas.sports;

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** No implicit operational policy: enabling the adapter requires explicit DEV settings. */
@ConfigurationProperties(prefix = "trevo.sports")
public record SportsProperties(
    boolean enabled,
    boolean syncEnabled,
    String policy,
    String apiKey,
    Set<Integer> leagueAllowlist,
    Set<Integer> seasonAllowlist,
    Set<Integer> bookmakerAllowlist,
    Set<Integer> marketAllowlist,
    int dailyRequestBudget,
    int requestReserve,
    String quotaStatePath,
    int connectTimeoutMs,
    int readTimeoutMs,
    int maxRunSeconds,
    int maxPages,
    int maxRetries,
    int maxConcurrentRuns,
    long maxPriceAgeSeconds,
    long maxScheduleAgeSeconds,
    long maxStatusAgeSeconds,
    Map<Integer, MarketMapping> mappings) {
  public SportsProperties {
    leagueAllowlist = leagueAllowlist == null ? Set.of() : Set.copyOf(leagueAllowlist);
    seasonAllowlist = seasonAllowlist == null ? Set.of() : Set.copyOf(seasonAllowlist);
    bookmakerAllowlist = bookmakerAllowlist == null ? Set.of() : Set.copyOf(bookmakerAllowlist);
    marketAllowlist = marketAllowlist == null ? Set.of() : Set.copyOf(marketAllowlist);
    mappings = mappings == null ? Map.of() : Map.copyOf(mappings);
  }

  public boolean configured() {
    return enabled
        && "DEV_EXPERIMENTAL".equals(policy)
        && apiKey != null
        && !apiKey.isBlank()
        && !"YOUR_API_KEY_HERE".equals(apiKey)
        && positive(leagueAllowlist)
        && positive(seasonAllowlist)
        && positive(bookmakerAllowlist)
        && positive(marketAllowlist)
        && mappings.keySet().containsAll(marketAllowlist)
        && marketAllowlist.stream().allMatch(id -> mappings.get(id).valid())
        && dailyRequestBudget > 1
        && dailyRequestBudget <= 100
        && requestReserve > 0
        && requestReserve < dailyRequestBudget
        && quotaStatePath != null
        && !quotaStatePath.isBlank()
        && connectTimeoutMs > 0
        && connectTimeoutMs <= 10000
        && readTimeoutMs > 0
        && readTimeoutMs <= 15000
        && maxRunSeconds > 0
        && maxRunSeconds <= 120
        && maxPages > 0
        && maxPages <= 100
        && maxRetries >= 0
        && maxRetries <= 2
        && maxConcurrentRuns == 1
        && maxPriceAgeSeconds > 0
        && maxPriceAgeSeconds <= 86400
        && maxScheduleAgeSeconds > 0
        && maxScheduleAgeSeconds <= 86400
        && maxStatusAgeSeconds > 0
        && maxStatusAgeSeconds <= 86400;
  }

  private static boolean positive(Set<Integer> values) {
    return !values.isEmpty() && values.stream().allMatch(i -> i != null && i > 0);
  }

  public record MarketMapping(
      String marketType,
      String displayName,
      String period,
      String ruleKey,
      String ruleText,
      Map<String, SelectionMapping> selections) {
    boolean valid() {
      return text(marketType, 64)
          && text(displayName, 255)
          && text(period, 64)
          && text(ruleKey, 255)
          && text(ruleText, 4000)
          && selections != null
          && !selections.isEmpty()
          && selections.entrySet().stream()
              .allMatch(e -> text(e.getKey(), 255) && e.getValue() != null && e.getValue().valid());
    }
  }

  public record SelectionMapping(
      String code, String displayName, String line, String participant, String specifiersKey) {
    boolean valid() {
      return code != null && code.matches("[A-Z0-9][A-Z0-9_:-]{0,63}") && text(displayName, 255);
    }
  }

  private static boolean text(String value, int max) {
    return value != null && !value.isBlank() && value.length() <= max;
  }

  // Deliberately never include apiKey in diagnostic text.
  @Override
  public String toString() {
    return "SportsProperties[redacted]";
  }
}
