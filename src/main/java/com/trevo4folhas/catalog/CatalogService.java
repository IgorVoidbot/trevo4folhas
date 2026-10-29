package com.trevo4folhas.catalog;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trevo4folhas.catalog.CatalogDtos.MatchView;
import com.trevo4folhas.catalog.CatalogRequests.CreateMatch;

@Service
@Transactional(readOnly = true)
public class CatalogService {
  private final CatalogRepository repository;
  private final Clock clock;

  public CatalogService(CatalogRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(timeout = 5)
  public MatchView createMatch(CreateMatch request) {
    if (request.homeTeam().strip().equalsIgnoreCase(request.awayTeam().strip())) {
      throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_TEAMS", "Mandante e visitante devem ser diferentes.");
    }
    if (!request.startsAt().isAfter(clock.instant())) {
      throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_KICKOFF", "O início da partida deve estar no futuro.");
    }
    UUID id = UUID.randomUUID();
    repository.insertMatch(id, request, clock.instant());
    return repository.match(id, false);
  }
}
