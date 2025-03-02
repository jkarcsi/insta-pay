package com.kibitsolutions.instapay.service;

import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import com.kibitsolutions.instapay.exception.AccountNotFoundException;
import com.kibitsolutions.instapay.exception.InsufficientFundsException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentService {

    @Autowired
    private AccountRepository accountRepo;

    @Autowired
    private TransactionRepository transactionRepo;

    @Autowired
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public PaymentService(AccountRepository accountRepo,
                          TransactionRepository transactionRepo,
                          KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.accountRepo = accountRepo;
        this.transactionRepo = transactionRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    @Retryable(maxAttempts = 5,
            backoff = @Backoff(delay = 5000),
            noRetryFor = {InsufficientFundsException.class, AccountNotFoundException.class})
    @CircuitBreaker(name = "paymentServiceCircuitBreaker", fallbackMethod = "fallbackProcessPayment")
    public Transaction processPayment(String fromAccountId, String toAccountId, BigDecimal amount) {
        Account from = accountRepo.findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found: " + fromAccountId));

        Account to = accountRepo.findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Recipient account not found: " + toAccountId));

        // Balance check
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance in sender account: " + fromAccountId);
        }
        // Account check
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Sender and recipient account are the same!");
        }

        // Deduct and credit
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        // Save updated accounts
        accountRepo.save(from);
        accountRepo.save(to);

        // Record the transaction
        Transaction transaction = new Transaction(from.getId(), to.getId(), amount, Instant.now());
        transactionRepo.save(transaction);

        // Publish event for notification
        TransactionEvent event = new TransactionEvent(
                transaction.getId(),
                from.getId(),
                to.getId(),
                amount
        );
        kafkaTemplate.send("payments", event);

        return transaction;
    }

    public Optional<Transaction> getTransactionById(Long id) {
        return transactionRepo.findById(id);
    }

    @Recover
    public Transaction fallbackProcessPayment(
            Exception ex,
            String fromAccountId,
            String toAccountId,
            BigDecimal amount
    ) {
        System.err.println("fallbackProcessPayment triggered: " + ex.getMessage());

        Transaction failedTx = new Transaction();
        failedTx.setFromAccountId(fromAccountId);
        failedTx.setToAccountId(toAccountId);
        failedTx.setAmount(BigDecimal.ZERO);
        return failedTx;
    }

    public BigDecimal getAccountBalance(String accountId) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return account.getBalance();
    }

}
