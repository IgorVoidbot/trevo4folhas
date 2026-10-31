package com.trevo4folhas.sports;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Synthetic contract samples, never evidence of authenticated provider access or parity. */
final class SportsTestSupport {
  static SportsProperties properties(Path path) {
    var mapping =
        new SportsProperties.MarketMapping(
            "1X2",
            "Resultado da partida",
            "FT",
            "REGULATION_TIME",
            "Tempo regulamentar, incluindo acréscimos.",
            Map.of(
                "Home",
                new SportsProperties.SelectionMapping("HOME", "Casa", null, null, null),
                "Draw",
                new SportsProperties.SelectionMapping("DRAW", "Empate", null, null, null),
                "Away",
                new SportsProperties.SelectionMapping("AWAY", "Visitante", null, null, null)));
    return new SportsProperties(
        true,
        false,
        "DEV_EXPERIMENTAL",
        "synthetic-test-key",
        Set.of(999),
        Set.of(2030),
        Set.of(99),
        Set.of(1),
        10,
        2,
        path.toString(),
        100,
        100,
        60,
        5,
        0,
        1,
        3600,
        7200,
        1800,
        Map.of(1, mapping));
  }

  static JsonNode sample(String name, ObjectMapper mapper) throws java.io.IOException {
    try (var input =
        SportsTestSupport.class.getResourceAsStream("/fixtures/api-football/" + name + ".json")) {
      return mapper.readTree(input);
    }
  }

  static ObjectMapper mapper() {
    return new ObjectMapper()
        .findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
