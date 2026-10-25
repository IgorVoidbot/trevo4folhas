package com.trevo4folhas.ticket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.trevo4folhas.admin.Admin;
import com.trevo4folhas.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A pre-ticket ({@code tickets}). The physical code column is {@code ticket_code}; there is no
 * duplicated {@code code} column. Manual decisions always carry operator and timestamp, and the
 * bettor fields are personal data that must never reach logs or public responses.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

  /** Ticket code; the physical column is {@code ticket_code}. */
  @Column(name = "ticket_code", nullable = false, length = 32)
  private String code;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  /** SHA-256 hex; the schema keeps it in {@code char(64)} (blank-padded), not varchar. */
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  /** SHA-256 hex; the schema keeps it in {@code char(64)} (blank-padded), not varchar. */
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "session_hash", nullable = false, length = 64)
  private String sessionHash;

  @Column(name = "bettor_name", nullable = false, length = 100)
  private String bettorName;

  @Column(name = "bettor_phone", nullable = false, length = 15)
  private String bettorPhone;

  @Column(name = "stake_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal stakeAmount;

  @Column(name = "total_odds", nullable = false, precision = 41, scale = 20)
  private BigDecimal totalOdds;

  @Column(name = "potential_return", nullable = false, precision = 14, scale = 2)
  private BigDecimal potentialReturn;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private TicketStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "handled_by")
  private Admin handledBy;

  @Column(name = "handled_at")
  private Instant handledAt;

  @Column(name = "cancellation_reason", length = 250)
  private String cancellationReason;

  @Column(name = "operator_note", length = 500)
  private String operatorNote;

  @Column(name = "whatsapp_clicked_at")
  private Instant whatsappClickedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 1L;

  protected Ticket() {
    // For JPA.
  }

  public Ticket(
      String code,
      UUID requestKey,
      String requestHash,
      String sessionHash,
      String bettorName,
      String bettorPhone,
      BigDecimal stakeAmount,
      BigDecimal totalOdds,
      BigDecimal potentialReturn,
      Instant expiresAt) {
    this.code = code;
    this.requestKey = requestKey;
    this.requestHash = requestHash;
    this.sessionHash = sessionHash;
    this.bettorName = bettorName;
    this.bettorPhone = bettorPhone;
    this.stakeAmount = stakeAmount;
    this.totalOdds = totalOdds;
    this.potentialReturn = potentialReturn;
    this.expiresAt = expiresAt;
    this.status = TicketStatus.PENDING;
  }

  public String getCode() {
    return code;
  }

  public UUID getRequestKey() {
    return requestKey;
  }

  public String getBettorName() {
    return bettorName;
  }

  public String getBettorPhone() {
    return bettorPhone;
  }

  public BigDecimal getStakeAmount() {
    return stakeAmount;
  }

  public BigDecimal getTotalOdds() {
    return totalOdds;
  }

  public BigDecimal getPotentialReturn() {
    return potentialReturn;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Admin getHandledBy() {
    return handledBy;
  }

  public Instant getHandledAt() {
    return handledAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public String getOperatorNote() {
    return operatorNote;
  }

  public Instant getWhatsappClickedAt() {
    return whatsappClickedAt;
  }

  public Long getVersion() {
    return version;
  }

  /** WhatsApp click bookkeeping: operational metadata, not a commercial change. */
  public void recordWhatsappClickedAt(Instant clickedAt) {
    this.whatsappClickedAt = clickedAt;
  }
}
