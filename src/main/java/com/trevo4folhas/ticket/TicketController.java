package com.trevo4folhas.ticket;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class TicketController {
  private static final String NONCE = "ticketSessionNonce";
  private final SecureRandom random = new SecureRandom();
  private final TicketService service;
  private final TicketWriter writer;
  private final TicketRateLimiter limiter;

  public TicketController(TicketService service, TicketWriter writer, TicketRateLimiter limiter) {
    this.service = service;
    this.writer = writer;
    this.limiter = limiter;
  }

  @GetMapping("/api/session")
  public ResponseEntity<Map<String, String>> session(CsrfToken csrf, HttpSession session) {
    sessionHash(session);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("headerName", csrf.getHeaderName(), "token", csrf.getToken()));
  }

  @PostMapping("/api/tickets")
  public ResponseEntity<TicketReceipt> create(
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody TicketRequests.CreateTicket body,
      HttpServletRequest request,
      HttpSession session) {
    String hash = sessionHash(session);
    limiter.check(request.getRemoteAddr(), hash);
    var result = service.create(key, hash, body);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .cacheControl(CacheControl.noStore())
        .body(result.receipt());
  }

  @PostMapping("/api/tickets/whatsapp-click")
  public ResponseEntity<Void> click(
      @Valid @RequestBody TicketRequests.WhatsappClick body, HttpSession session) {
    writer.click(body.requestKey(), sessionHash(session));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private String sessionHash(HttpSession session) {
    synchronized (session) {
      String nonce = (String) session.getAttribute(NONCE);
      if (nonce == null) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        nonce = HexFormat.of().formatHex(bytes);
        session.setAttribute(NONCE, nonce);
      }
      return TicketIntent.hash(nonce);
    }
  }
}
