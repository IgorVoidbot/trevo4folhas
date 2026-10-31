package com.trevo4folhas.sports;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

class ProviderMappingTest {
  private final com.fasterxml.jackson.databind.ObjectMapper mapper = SportsTestSupport.mapper();
  private final ProviderMapping mapping =
      new ProviderMapping(SportsTestSupport.properties(Path.of("/tmp/unused-quota")));

  @Test
  void explicitSyntheticMappingPreservesIdentityAndCanonicalAbsence() throws Exception {
    var fixtures = mapping.fixtures(SportsTestSupport.sample("fixtures", mapper).path("response"));
    var offers = mapping.offers(SportsTestSupport.sample("odds", mapper).path("response"));
    assertThat(fixtures.get(0).externalId()).isEqualTo("900001");
    assertThat(offers).hasSize(3);
    assertThat(offers.get(0).selectionCode()).isEqualTo("HOME");
    assertThat(offers.get(0).line()).isEqualTo("NONE");
    assertThat(offers.get(0).specifiersKey()).isEqualTo("NONE");
    assertThat(offers.get(0).odds()).isEqualByComparingTo("1.85");
  }

  @Test
  void invalidPrecisionIsQuarantinedWithoutRounding() throws Exception {
    for (String price : java.util.List.of("1.851", "NaN", "0.99", "100.01", "")) {
      var rows = SportsTestSupport.sample("odds", mapper).path("response");
      ((ObjectNode) rows.get(0).path("bookmakers").get(0).path("bets").get(0).path("values").get(0))
          .put("odd", price);
      assertThat(mapping.offers(rows).get(0).odds()).isNull();
    }
  }

  @Test
  void unknownSelectionsAndWrongNamespacesAreNotSilentlyMapped() throws Exception {
    var rows = SportsTestSupport.sample("odds", mapper).path("response");
    ((ObjectNode) rows.get(0).path("bookmakers").get(0).path("bets").get(0).path("values").get(0))
        .put("value", "Unknown");
    assertThatThrownBy(() -> mapping.offers(rows))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("UNMAPPED_SELECTION");
    var fixtures = SportsTestSupport.sample("fixtures", mapper).path("response");
    ((ObjectNode) fixtures.get(0).path("league")).put("id", 1);
    assertThatThrownBy(() -> mapping.fixtures(fixtures))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("UNEXPECTED_PROVIDER_IDENTITY");
  }

  @Test
  void fractionalAndCoercedIdentitiesCannotEnterAnAllowedNamespace() throws Exception {
    for (String identity : java.util.List.of("999.5", "\"999\"", "null", "true")) {
      var fixtures = SportsTestSupport.sample("fixtures", mapper).path("response");
      ((ObjectNode) fixtures.get(0).path("league")).set("id", mapper.readTree(identity));
      assertThatThrownBy(() -> mapping.fixtures(fixtures))
          .isInstanceOf(SyncFailure.class)
          .hasMessageContaining("INVALID_PROVIDER_ID");
    }
    var rows = SportsTestSupport.sample("odds", mapper).path("response");
    ((ObjectNode) rows.get(0).path("bookmakers").get(0).path("bets").get(0)).put("id", 1.5);
    assertThatThrownBy(() -> mapping.offers(rows))
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("INVALID_PROVIDER_ID");
  }

  @Test
  void missingPriceTimestampStaysUnknownAndSecretsAreNotPrintable() throws Exception {
    var rows = SportsTestSupport.sample("odds", mapper).path("response");
    ((ObjectNode) rows.get(0)).remove("update");
    assertThat(mapping.offers(rows).get(0).updatedAt()).isNull();
    assertThat(SportsTestSupport.properties(Path.of("/tmp/unused")).toString())
        .doesNotContain("synthetic-test-key");
  }
}
