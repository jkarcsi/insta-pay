package com.kibitsolutions.instapay.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.annotation.Version;

import java.math.BigDecimal;

@Entity
@Data
public class Account {
    @Id
    private String id;
    private BigDecimal balance;
    @Version
    private Long version;
}
