package com.trevo4folhas.market;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MarketRepository extends JpaRepository<Market, UUID> {

  List<Market> findByMatchId(UUID matchId);

  /**
   * Records one sync request attempt (operational metadata) without renewing the commercial {@code
   * version} of the market. Post-bulk semantics: the statement bypasses the persistence context, so
   * managed instances keep their in-memory value until re-read while the database row stays
   * authoritative; the operational columns are mapped {@code updatable = false}, so later
   * commercial flushes (same or another transaction) can never overwrite the recorded attempt.
   */
  @Modifying
  @Transactional
  @Query("update Market m set m.requestAttemptAt = :attemptAt where m.id = :id")
  int recordRequestAttempt(@Param("id") UUID id, @Param("attemptAt") Instant attemptAt);
}
