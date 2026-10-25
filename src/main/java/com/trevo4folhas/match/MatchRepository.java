package com.trevo4folhas.match;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MatchRepository extends JpaRepository<Match, UUID> {

  Optional<Match> findBySourceNamespaceAndExternalFixtureId(
      String sourceNamespace, String externalFixtureId);

  /**
   * Records one sync request attempt. This is operational metadata and runs as a bulk update, so
   * the commercial {@code version} of the match is never renewed by mere bookkeeping. Post-bulk
   * semantics: the statement bypasses the persistence context, so managed instances keep their
   * in-memory value until re-read while the database row stays authoritative; the operational
   * columns are mapped {@code updatable = false}, so later commercial flushes (same or another
   * transaction) can never overwrite the recorded attempt.
   */
  @Modifying
  @Transactional
  @Query("update Match m set m.requestAttemptAt = :attemptAt where m.id = :id")
  int recordRequestAttempt(@Param("id") UUID id, @Param("attemptAt") Instant attemptAt);
}
