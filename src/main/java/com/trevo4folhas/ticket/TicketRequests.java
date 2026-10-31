package com.trevo4folhas.ticket;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.trevo4folhas.catalog.CatalogRequests.StrictRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class TicketRequests {
  private TicketRequests() {}

  public record CreateTicket(
      @NotBlank @Size(max = 200) String bettorName,
      @NotBlank @Size(max = 32) String bettorPhone,
      @NotNull
          @DecimalMin("0.01")
          @Digits(integer = 12, fraction = 2)
          @JsonDeserialize(using = AmountDeserializer.class)
          BigDecimal stakeAmount,
      @AssertTrue boolean rulesAccepted,
      @AssertTrue boolean adultDeclared,
      @NotBlank @Size(max = 64) String rulesVersion,
      @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Item> items)
      implements StrictRequest {
    @Override
    public String toString() {
      return "CreateTicket[redacted]";
    }
  }

  public record Item(
      @NotNull UUID selectionId,
      @NotNull @Positive Long matchVersion,
      @NotNull @Positive Long marketVersion,
      @NotNull @Positive Long selectionVersion)
      implements StrictRequest {}

  public record WhatsappClick(@NotNull UUID requestKey) implements StrictRequest {}

  public static class AmountDeserializer extends JsonDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (parser.currentToken() != JsonToken.VALUE_STRING
          || !parser.getText().matches("[0-9]{1,12}(\\.[0-9]{1,2})?"))
        return context.reportInputMismatch(
            BigDecimal.class, "Informe um valor decimal em texto, com até duas casas.");
      return new BigDecimal(parser.getText());
    }
  }
}
