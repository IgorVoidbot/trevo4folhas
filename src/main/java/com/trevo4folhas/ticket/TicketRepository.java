package com.trevo4folhas.ticket;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

  Optional<Ticket> findByCode(String code);

  Optional<Ticket> findByRequestKey(UUID requestKey);
}
