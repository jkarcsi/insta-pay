package com.kibitsolutions.instapay.resilience;

import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import com.kibitsolutions.instapay.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class PaymentServiceResilienceTest {

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

        // Retrieve the circuit breaker instance by name and transition it to open state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentServiceCircuitBreaker");
        circuitBreaker.transitionToOpenState();

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

    /**
     * Simulate transient errors for the first two attempts and a successful call on the third.
     * This verifies that retry logic recovers eventually.
     */
    @Test
    void testProcessPayment_RetrySuccess() {
        Account sender = new Account();
        sender.setId("acc1");
        sender.setBalance(BigDecimal.valueOf(1000));
        Account recipient = new Account();
        recipient.setId("acc2");
        recipient.setBalance(BigDecimal.valueOf(500));

        when(accountRepo.findById("acc1")).thenReturn(Optional.of(sender));
        when(accountRepo.findById("acc2")).thenReturn(Optional.of(recipient));

        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger exceptionsThrown = new AtomicInteger(0);

        // Retrieve the circuit breaker instance by name and transition it to close state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentServiceCircuitBreaker");
        circuitBreaker.transitionToClosedState();

        when(transactionRepo.save(any(Transaction.class))).thenAnswer(invocation -> {
            if (counter.getAndIncrement() < 2) {
                // Simulate transient error on first two attempts.
                exceptionsThrown.incrementAndGet();
                throw new RuntimeException("Transient error");
            }
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        Transaction result = paymentService.processPayment("acc1", "acc2", BigDecimal.valueOf(200));

        // Verify that the payment eventually succeeded.
        assertNotNull(result);
        assertEquals("acc1", result.getFromAccountId());
        assertEquals("acc2", result.getToAccountId());
        assertEquals(BigDecimal.valueOf(700), recipient.getBalance());
        assertEquals(BigDecimal.valueOf(800), sender.getBalance());
        assertTrue(counter.get() >= 3);
        assertTrue(exceptionsThrown.get() >= 1);
    }

}
