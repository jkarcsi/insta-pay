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

    /**
     * Processes a payment transaction from one account to another.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the sender and recipient accounts from the repository.</li>
     *   <li>Checks if the sender has sufficient funds and that sender and recipient are not the same.</li>
     *   <li>Deducts the amount from the sender's balance and credits it to the recipient's balance.</li>
     *   <li>Saves the updated account information.</li>
     *   <li>Creates and saves a Transaction record.</li>
     *   <li>Sends a TransactionEvent message via Kafka for asynchronous notification.</li>
     * </ol>
     * <p>
     * This method is annotated with:
     * <ul>
     *   <li>{@code @Transactional} to ensure atomicity of database operations.</li>
     *   <li>{@code @Retryable} to retry the operation up to 5 times (with a 5000ms delay) on failure,
     *       except in cases of InsufficientFundsException or AccountNotFoundException.</li>
     *   <li>{@code @CircuitBreaker} to prevent cascading failures, with fallback method {@code fallbackProcessPayment}.</li>
     * </ul>
     *
     * @param fromAccountId the ID of the sender account
     * @param toAccountId   the ID of the recipient account
     * @param amount        the amount to transfer
     * @return the Transaction record representing the processed payment
     * @throws InsufficientFundsException if the sender does not have enough balance
     * @throws AccountNotFoundException   if either the sender or recipient account is not found
     */
    @Transactional
    @Retryable(maxAttempts = 5,
            backoff = @Backoff(delay = 5000),
            noRetryFor = {InsufficientFundsException.class, AccountNotFoundException.class})
    @CircuitBreaker(name = "paymentServiceCircuitBreaker", fallbackMethod = "fallbackProcessPaymentCircuitBreaker")
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

    /**
     * Retrieves a transaction by its ID.
     *
     * @param id the ID of the transaction to retrieve
     * @return an Optional containing the Transaction if found, or empty if not found
     */
    public Optional<Transaction> getTransactionById(Long id) {
        return transactionRepo.findById(id);
    }

    /**
     * Fallback method for the Circuit Breaker when {@code processPayment} fails.
     * <p>
     * This method is invoked by Resilience4j's Circuit Breaker if the {@code processPayment} method
     * fails or the circuit is open.
     *
     * @param fromAccountId the ID of the sender account
     * @param toAccountId   the ID of the recipient account
     * @param amount        the amount that was attempted to be transferred
     * @param throwable     the exception that triggered the fallback
     * @return a Transaction object representing a failed transaction (with the amount set to zero)
     */
    public Transaction fallbackProcessPaymentCircuitBreaker(String fromAccountId, String toAccountId, BigDecimal amount, Throwable throwable) {
        System.err.println("Circuit Breaker fallback triggered: " + throwable.getMessage());

        Transaction failedTx = new Transaction();
        failedTx.setFromAccountId(fromAccountId);
        failedTx.setToAccountId(toAccountId);
        failedTx.setAmount(BigDecimal.ZERO);

        return failedTx;
    }

    /**
     * Fallback method for Spring Retry when {@code processPayment} fails after all retry attempts.
     * <p>
     * This method is invoked by Spring Retry's recovery mechanism.
     *
     * @param ex            the exception that caused the failure
     * @param fromAccountId the ID of the sender account
     * @param toAccountId   the ID of the recipient account
     * @param amount        the amount that was attempted to be transferred
     * @return a Transaction object representing a failed transaction (with the amount set to zero)
     */
    @Recover
    public Transaction fallbackProcessPayment(Exception ex, String fromAccountId, String toAccountId, BigDecimal amount) {
        System.err.println("Retry fallback triggered: " + ex.getMessage());

        Transaction failedTx = new Transaction();
        failedTx.setFromAccountId(fromAccountId);
        failedTx.setToAccountId(toAccountId);
        failedTx.setAmount(BigDecimal.ZERO);

        return failedTx;
    }

    /**
     * Retrieves the balance of the account identified by the given account ID.
     *
     * @param accountId the ID of the account
     * @return the balance of the account as a BigDecimal
     * @throws AccountNotFoundException if the account with the specified ID does not exist
     */
    public BigDecimal getAccountBalance(String accountId) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return account.getBalance();
    }

}
