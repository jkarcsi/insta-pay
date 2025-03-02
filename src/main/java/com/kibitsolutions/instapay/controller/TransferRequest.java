package com.kibitsolutions.instapay.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequest {
    @NotNull(message = "Sender account must not be null")
    private String fromAcct;
    @NotNull(message = "Recipient account must not be null")
    private String toAcct;
    @NotNull(message = "Amount must not be null")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}
