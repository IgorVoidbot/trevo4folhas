package com.trevo4folhas.catalog;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trevo4folhas.catalog.CatalogDtos.MarketView;
import com.trevo4folhas.catalog.CatalogDtos.MatchView;
import com.trevo4folhas.catalog.CatalogDtos.SelectionView;
import com.trevo4folhas.catalog.CatalogRequests.CreateMarket;
import com.trevo4folhas.catalog.CatalogRequests.CreateMatch;
import com.trevo4folhas.catalog.CatalogRequests.CreateSelection;

import jakarta.validation.Valid;

@RestController
public class AdminCatalogController {
  private final CatalogService service;

  public AdminCatalogController(CatalogService service) {
    this.service = service;
  }

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
  public SelectionView createSelection(
      @PathVariable UUID id, @Valid @RequestBody CreateSelection request) {
    return service.createSelection(id, request);
  }

  @org.springframework.web.bind.annotation.PatchMapping("/api/admin/matches/{id}")
  public MatchView updateMatch(
      @PathVariable UUID id, @Valid @RequestBody CatalogRequests.UpdateMatch request) {
    return service.updateMatch(id, request);
  }

  @org.springframework.web.bind.annotation.PatchMapping("/api/admin/markets/{id}")
  public MarketView updateMarket(
      @PathVariable UUID id, @Valid @RequestBody CatalogRequests.UpdateMarket request) {
    return service.updateMarket(id, request);
  }

  @org.springframework.web.bind.annotation.PatchMapping("/api/admin/selections/{id}")
  public SelectionView updateSelection(
      @PathVariable UUID id, @Valid @RequestBody CatalogRequests.UpdateSelection request) {
    return service.updateSelection(id, request);
  }
}
