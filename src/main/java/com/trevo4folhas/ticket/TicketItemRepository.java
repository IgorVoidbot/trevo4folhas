package com.trevo4folhas.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketItemRepository extends JpaRepository<TicketItem, UUID> {

  List<TicketItem> findByTicketId(UUID ticketId);

  boolean existsByTicketIdAndMatchId(UUID ticketId, UUID matchId);
}
