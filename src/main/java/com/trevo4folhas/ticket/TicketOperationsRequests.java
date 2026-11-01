package com.trevo4folhas.ticket;

import com.trevo4folhas.catalog.CatalogRequests.StrictRequest;

import jakarta.validation.constraints.*;

public final class TicketOperationsRequests {
  private TicketOperationsRequests() {}

  public record Confirm(
      @NotNull @Positive Long ticketVersion,
      @AssertTrue boolean pixReceiptChecked,
      @Size(max = 500) String operatorNote)
      implements StrictRequest {
    @Override
    public String toString() {
      return "ConfirmTicket[redacted]";
    }
  }

  public record Cancel(
      @NotNull @Positive Long ticketVersion,
      @NotBlank @Size(max = 250) String reason,
      @Size(max = 500) String operatorNote)
      implements StrictRequest {
    @Override
    public String toString() {
      return "CancelTicket[redacted]";
    }
  }
}
