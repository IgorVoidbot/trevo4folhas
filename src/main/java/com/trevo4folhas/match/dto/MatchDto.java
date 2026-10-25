package com.trevo4folhas.match.dto;

import java.time.Instant;
import java.util.UUID;

import com.trevo4folhas.match.Match;
import com.trevo4folhas.match.MatchStatus;

/** Catalog-facing view of a match. Carries no JPA types and no internal metadata. */
public record MatchDto(
    UUID id,
    String competition,
    String homeTeam,
    String awayTeam,
    Instant startsAt,
    MatchStatus status) {

  public static MatchDto from(Match match) {
    return new MatchDto(
        match.getId(),
        match.getCompetition(),
        match.getHomeTeam(),
        match.getAwayTeam(),
        match.getStartsAt(),
        match.getStatus());
  }
}
