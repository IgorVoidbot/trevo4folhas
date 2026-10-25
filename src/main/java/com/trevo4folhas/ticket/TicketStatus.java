package com.trevo4folhas.ticket;

/** Lifecycle of a pre-ticket, stored verbatim in {@code tickets.status}. */
public enum TicketStatus {
  PENDING,
  CONFIRMED,
  CANCELLED,
  EXPIRED
}
