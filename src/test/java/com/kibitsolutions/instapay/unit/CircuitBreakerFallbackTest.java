package com.kibitsolutions.instapay.unit;

import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import com.kibitsolutions.instapay.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
)
@TestPropertySource(properties =
        "spring.main.allow-bean-definition-overriding=true"
)
@ActiveProfiles("test")
class CircuitBreakerFallbackTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountRepository accountRepo;

    @Autowired
    private TransactionRepository transactionRepo;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public AccountRepository accountRepo() {
            return mock(AccountRepository.class);
        }

        @Bean
        public TransactionRepository transactionRepo() {
            return mock(TransactionRepository.class);
        }

        @Bean
        public KafkaTemplate<String, TransactionEvent> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }

    @BeforeEach
    void setUp() {
        // Retrieve the circuit breaker instance by name and transition it to open state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentServiceCircuitBreaker");
        circuitBreaker.transitionToOpenState();
    }

    /**
     * Simulate a persistent error so that the circuit breaker fallback method is triggered.
     * The fallback should return a Transaction with an amount of zero.
     */
    @Test
    void testProcessPayment_CircuitBreakerFallback() {
        Account sender = new Account();
        sender.setId("acc1");
        sender.setBalance(BigDecimal.valueOf(1000));
        Account recipient = new Account();
        recipient.setId("acc2");
        recipient.setBalance(BigDecimal.valueOf(500));

        when(accountRepo.findById("acc1")).thenReturn(Optional.of(sender));
        when(accountRepo.findById("acc2")).thenReturn(Optional.of(recipient));

        // Force a persistent error by always throwing an exception from transactionRepo.save.
        when(transactionRepo.save(any(Transaction.class)))
                .thenThrow(new RuntimeException("Persistent error"));

        Transaction result = paymentService.processPayment("acc1", "acc2", BigDecimal.valueOf(200));

        // Verify that the circuit breaker fallback returns a Transaction with zero amount.
        assertNotNull(result);
        assertEquals("acc1", result.getFromAccountId());
        assertEquals("acc2", result.getToAccountId());
        assertEquals(BigDecimal.ZERO, result.getAmount());
    }
}
