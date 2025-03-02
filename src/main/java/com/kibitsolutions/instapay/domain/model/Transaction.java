package com.kibitsolutions.instapay.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Data
public class Transaction {

    @Id
    @GeneratedValue
    private Long id;

    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private Instant timestamp;

    public Transaction(String fromAccountId, String toAccountId, BigDecimal amount, Instant timestamp) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public Transaction() {
        // for JPA
    }
}
