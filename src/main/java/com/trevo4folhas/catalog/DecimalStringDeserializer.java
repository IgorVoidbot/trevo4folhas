package com.trevo4folhas.catalog;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/** Decimals cross the HTTP boundary as strings, without binary-float coercion. */
public class DecimalStringDeserializer extends JsonDeserializer<BigDecimal> {
  @Override
  public BigDecimal deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_STRING
        || !parser.getText().matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) {
      return context.reportInputMismatch(
          BigDecimal.class, "Informe uma cotação decimal em texto, com até duas casas.");
    }
    return new BigDecimal(parser.getText());
  }
}
