package com.trevo4folhas.sports;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trevo4folhas.catalog.CatalogRequests.StrictRequest;

import jakarta.validation.Valid;

@RestController
public class CatalogSyncController {
  private final CatalogSyncService service;

  public CatalogSyncController(CatalogSyncService service) {
    this.service = service;
  }

  public record RunRequest() implements StrictRequest {}

  @GetMapping("/api/admin/catalog-sync/status")
  public ResponseEntity<CatalogSyncService.Status> status() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());
  }

  @PostMapping("/api/admin/catalog-sync")
  public ResponseEntity<CatalogSyncService.Status> run(@Valid @RequestBody RunRequest request) {
    var result = service.run();
    return ResponseEntity.status(result.enabled() ? HttpStatus.OK : HttpStatus.CONFLICT)
        .cacheControl(CacheControl.noStore())
        .body(result);
  }
}
