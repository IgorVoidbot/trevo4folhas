package com.trevo4folhas.sports;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public interface SportsDataProvider {
  enum Endpoint {
    FIXTURES,
    ODDS
  }

  record Page(JsonNode rows, int totalPages, int dailyRemaining, int minuteRemaining) {}

  Page fetch(Endpoint endpoint, Map<String, Integer> parameters, Instant deadline);
}
