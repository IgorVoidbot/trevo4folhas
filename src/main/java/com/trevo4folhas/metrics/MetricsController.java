package com.trevo4folhas.metrics;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.trevo4folhas.admin.AdminUserDetails;

@RestController
public class MetricsController {
  private final MetricsService service;

  public MetricsController(MetricsService service) {
    this.service = service;
  }

  @GetMapping("/api/admin/metrics")
  public ResponseEntity<OperationalMetrics> read(
      @AuthenticationPrincipal AdminUserDetails actor,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.read(actor == null ? null : actor.getAdminId(), from, to));
  }
}
