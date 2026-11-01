package com.trevo4folhas.ticket;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.trevo4folhas.admin.AdminUserDetails;
import com.trevo4folhas.ticket.TicketOperationsDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {
  private final TicketOperationsService service;

  public AdminTicketController(TicketOperationsService service) {
    this.service = service;
  }

  private UUID actor(AdminUserDetails principal) {
    return principal == null ? null : principal.getAdminId();
  }

  @GetMapping
  public ResponseEntity<TicketQueue> list(
      @AuthenticationPrincipal AdminUserDetails principal,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) TicketStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.list(actor(principal), code, status, from, to, page, size));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Detail> detail(
      @PathVariable UUID id, @AuthenticationPrincipal AdminUserDetails principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.detail(id, actor(principal)));
  }

  @PostMapping("/{id}/confirm")
  public ResponseEntity<Decision> confirm(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserDetails principal,
      @Valid @RequestBody TicketOperationsRequests.Confirm request) {
    return response(service.confirm(id, actor(principal), request));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<Decision> cancel(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserDetails principal,
      @Valid @RequestBody TicketOperationsRequests.Cancel request) {
    return response(service.cancel(id, actor(principal), request));
  }

  private ResponseEntity<Decision> response(Decision decision) {
    return ResponseEntity.status(decision.changed() ? HttpStatus.OK : HttpStatus.CONFLICT)
        .cacheControl(CacheControl.noStore())
        .body(decision);
  }
}
