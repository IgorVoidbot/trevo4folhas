package com.trevo4folhas.catalog;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.trevo4folhas.catalog.CatalogDtos.*;
import com.trevo4folhas.match.MatchStatus;

@RestController
public class CatalogReadController {
  private final CatalogService service;

  public CatalogReadController(CatalogService service) {
    this.service = service;
  }

  @GetMapping("/api/matches")
  public Page<MatchView> matches(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) String competition,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.matches(false, date, competition, null, page, size);
  }

  @GetMapping("/api/admin/matches")
  public Page<MatchView> adminMatches(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) String competition,
      @RequestParam(required = false) MatchStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.matches(true, date, competition, status, page, size);
  }

  @GetMapping("/api/matches/{id}")
  public MatchView match(@PathVariable UUID id) {
    return service.match(id, false);
  }

  @GetMapping("/api/admin/matches/{id}")
  public MatchView adminMatch(@PathVariable UUID id) {
    return service.match(id, true);
  }

  @GetMapping("/api/matches/{id}/markets")
  public Page<MarketView> markets(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.markets(id, false, page, size);
  }

  @GetMapping("/api/admin/matches/{id}/markets")
  public Page<MarketView> adminMarkets(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.markets(id, true, page, size);
  }

  @GetMapping("/api/markets/{id}/selections")
  public Page<SelectionView> selections(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.selections(id, false, page, size);
  }

  @GetMapping("/api/admin/markets/{id}/selections")
  public Page<SelectionView> adminSelections(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.selections(id, true, page, size);
  }
}
