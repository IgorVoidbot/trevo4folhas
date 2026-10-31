package com.trevo4folhas.ticket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.*;

@Validated
@ConfigurationProperties(prefix = "trevo.security.ticket-rate-limit")
public record TicketRateProperties(
    @DefaultValue("10") @Min(1) @Max(1000) int maxAttempts,
    @DefaultValue("60") @Min(1) @Max(3600) int windowSeconds) {}
