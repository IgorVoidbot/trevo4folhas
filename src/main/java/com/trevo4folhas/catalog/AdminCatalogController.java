package com.trevo4folhas.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

import com.trevo4folhas.catalog.CatalogDtos.MatchView;
import com.trevo4folhas.catalog.CatalogRequests.CreateMatch;
import com.trevo4folhas.catalog.CatalogRequests.CreateMarket;
import com.trevo4folhas.catalog.CatalogRequests.CreateSelection;
import com.trevo4folhas.catalog.CatalogDtos.MarketView;
import com.trevo4folhas.catalog.CatalogDtos.SelectionView;

import jakarta.validation.Valid;

@RestController
public class AdminCatalogController {
  private final CatalogService service;

  public AdminCatalogController(CatalogService service) { this.service = service; }

  @PostMapping("/api/admin/matches")
  @ResponseStatus(HttpStatus.CREATED)
  public MatchView createMatch(@Valid @RequestBody CreateMatch request) {
    return service.createMatch(request);
  }

  @PostMapping("/api/admin/matches/{id}/markets")
  @ResponseStatus(HttpStatus.CREATED)
  public MarketView createMarket(@PathVariable UUID id, @Valid @RequestBody CreateMarket request) {
    return service.createMarket(id, request);
  }

  @PostMapping("/api/admin/markets/{id}/selections")
  @ResponseStatus(HttpStatus.CREATED)
  public SelectionView createSelection(@PathVariable UUID id, @Valid @RequestBody CreateSelection request) {
    return service.createSelection(id, request);
  }
}
