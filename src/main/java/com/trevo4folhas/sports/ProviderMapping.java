package com.trevo4folhas.sports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.trevo4folhas.catalog.CatalogService;

/** Exact configured mappings, never guessing equivalence from a provider's display label. */
@Component
public class ProviderMapping {
  public record Fixture(
      String externalId,
      String competition,
      String home,
      String away,
      Instant startsAt,
      String status) {}

  public record Offer(
      String fixtureId,
      String bookmakerId,
      String marketId,
      String marketType,
      String marketName,
      String period,
      String line,
      String participant,
      String ruleKey,
      String ruleText,
      String specifiersKey,
      String selectionKey,
      String selectionCode,
      String selectionName,
      BigDecimal odds,
      Instant updatedAt) {}

  private final SportsProperties properties;

  public ProviderMapping(SportsProperties properties) {
    this.properties = properties;
  }

  public List<Fixture> fixtures(JsonNode rows) {
    List<Fixture> result = new ArrayList<>();
    for (JsonNode row : rows) {
      if (!properties
              .leagueAllowlist()
              .contains(Integer.valueOf(positiveId(row.path("league").path("id"))))
          || !properties
              .seasonAllowlist()
              .contains(Integer.valueOf(positiveId(row.path("league").path("season")))))
        throw new SyncFailure("UNEXPECTED_PROVIDER_IDENTITY");
      JsonNode fixture = row.path("fixture");
      String external = positiveId(fixture.path("id"));
      String status =
          switch (fixture.path("status").path("short").asText()) {
            case "NS" -> "OPEN";
            case "CANC", "ABD", "AWD", "WO" -> "CANCELLED";
            case "PST", "TBD", "SUSP", "INT" -> "SUSPENDED";
            default -> "CLOSED";
          };
      String home = text(row.path("teams").path("home").path("name"), 120);
      String away = text(row.path("teams").path("away").path("name"), 120);
      if (home.equalsIgnoreCase(away)) throw new SyncFailure("INVALID_PROVIDER_FIXTURE");
      result.add(
          new Fixture(
              external,
              text(row.path("league").path("name"), 120),
              home,
              away,
              timestamp(fixture.path("date")),
              status));
    }
    return result;
  }

  public List<Offer> offers(JsonNode rows) {
    List<Offer> result = new ArrayList<>();
    for (JsonNode row : rows) {
      if (!properties
              .leagueAllowlist()
              .contains(Integer.valueOf(positiveId(row.path("league").path("id"))))
          || !properties
              .seasonAllowlist()
              .contains(Integer.valueOf(positiveId(row.path("league").path("season")))))
        throw new SyncFailure("UNEXPECTED_PROVIDER_IDENTITY");
      String fixtureId = positiveId(row.path("fixture").path("id"));
      Instant updatedAt = row.path("update").isTextual() ? timestamp(row.get("update")) : null;
      for (JsonNode bookmaker : array(row.path("bookmakers"))) {
        String bookmakerId = positiveId(bookmaker.path("id"));
        if (!properties.bookmakerAllowlist().contains(Integer.valueOf(bookmakerId)))
          throw new SyncFailure("UNMAPPED_BOOKMAKER");
        for (JsonNode bet : array(bookmaker.path("bets"))) {
          int id = Integer.parseInt(positiveId(bet.path("id")));
          if (!properties.marketAllowlist().contains(id) || !properties.mappings().containsKey(id))
            throw new SyncFailure("UNMAPPED_MARKET");
          SportsProperties.MarketMapping mapping = properties.mappings().get(id);
          for (JsonNode value : array(bet.path("values"))) {
            String key = text(value.path("value"), 255);
            SportsProperties.SelectionMapping selection = mapping.selections().get(key);
            if (selection == null) throw new SyncFailure("UNMAPPED_SELECTION");
            if (Set.of("HOME", "DRAW", "AWAY").contains(selection.code())
                != "1X2".equals(mapping.marketType()))
              throw new SyncFailure("INVALID_MARKET_MAPPING");
            String line;
            try {
              line = CatalogService.canonicalLine(selection.line());
            } catch (IllegalArgumentException invalid) {
              throw new SyncFailure("INVALID_MARKET_MAPPING");
            }
            String participant = CatalogService.absent(selection.participant());
            String specifiers = specifiers(selection.specifiersKey());
            if (participant.length() > 255
                || specifiers.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024)
              throw new SyncFailure("INVALID_MARKET_MAPPING");
            BigDecimal odds = null;
            try {
              String price = value.path("odd").asText();
              if (price.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) {
                BigDecimal parsed = new BigDecimal(price);
                if (parsed.compareTo(new BigDecimal("1.01")) >= 0
                    && parsed.compareTo(new BigDecimal("100.00")) <= 0) odds = parsed;
              }
            } catch (NumberFormatException invalid) {
              /* Quarantine invalid prices without rounding. */
            }
            result.add(
                new Offer(
                    fixtureId,
                    bookmakerId,
                    String.valueOf(id),
                    mapping.marketType(),
                    mapping.displayName(),
                    mapping.period(),
                    line,
                    participant,
                    mapping.ruleKey(),
                    mapping.ruleText(),
                    specifiers,
                    key,
                    selection.code(),
                    selection.displayName(),
                    odds,
                    updatedAt));
          }
        }
      }
    }
    return result;
  }

  private static String specifiers(String value) {
    if ("NONE".equals(CatalogService.absent(value))) return "NONE";
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      JsonNode node = mapper.readTree(value);
      if (!node.isObject() || node.size() > 16) throw new SyncFailure("INVALID_MARKET_MAPPING");
      Map<String, String> sorted = new TreeMap<>();
      var fields = node.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        if (!field.getKey().matches("[A-Za-z0-9_:-]{1,64}")
            || !field.getValue().isTextual()
            || field.getValue().asText().isBlank()
            || field.getValue().asText().length() > 128)
          throw new SyncFailure("INVALID_MARKET_MAPPING");
        sorted.put(field.getKey(), field.getValue().asText());
      }
      return sorted.isEmpty() ? "NONE" : mapper.writeValueAsString(sorted);
    } catch (java.io.IOException invalid) {
      throw new SyncFailure("INVALID_MARKET_MAPPING");
    }
  }

  private static JsonNode array(JsonNode node) {
    if (!node.isArray()) throw new SyncFailure("INVALID_PROVIDER_PAYLOAD");
    return node;
  }

  private static String positiveId(JsonNode node) {
    if (!node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < 1)
      throw new SyncFailure("INVALID_PROVIDER_ID");
    return node.asText();
  }

  private static String text(JsonNode node, int max) {
    if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > max)
      throw new SyncFailure("INVALID_PROVIDER_PAYLOAD");
    return node.asText().strip();
  }

  private static Instant timestamp(JsonNode node) {
    try {
      return Instant.parse(node.asText());
    } catch (RuntimeException invalid) {
      throw new SyncFailure("INVALID_PROVIDER_TIMESTAMP");
    }
  }
}
