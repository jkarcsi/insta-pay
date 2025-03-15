package com.kibitsolutions.instapay.service;

import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import com.kibitsolutions.instapay.exception.AccountNotFoundException;
import com.kibitsolutions.instapay.exception.IdenticalAccountException;
import com.kibitsolutions.instapay.exception.InsufficientFundsException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PaymentService {

    private final AccountRepository accountRepo;

    private final TransactionRepository transactionRepo;

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Autowired
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
     *   <li>Creates and saves a Transaction record.</li>
     *   <li>Deducts the amount from the sender's balance and credits it to the recipient's balance.</li>
     *   <li>Saves the updated account information.</li>
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
    @Transactional(rollbackFor = {Exception.class})
    @Retryable(
            maxAttempts = 5,
            backoff = @Backoff(delay = 5000),
            noRetryFor = {InsufficientFundsException.class, AccountNotFoundException.class, IdenticalAccountException.class},
            notRecoverable = {InsufficientFundsException.class, AccountNotFoundException.class, IdenticalAccountException.class}
    )
    @CircuitBreaker(
            name = "paymentServiceCircuitBreaker",
            fallbackMethod = "fallbackProcessPaymentCircuitBreaker"
    )
    public Transaction processPayment(String fromAccountId, String toAccountId, BigDecimal amount) {

        log.info("Starting to process payment from '{}' to '{}' with amount: {}",
                fromAccountId, toAccountId, amount);

        Account from = accountRepo.findByIdForTransaction(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found: " + fromAccountId));
        log.debug("Sender account found: {} with balance: {}", from.getId(), from.getBalance());

        Account to = accountRepo.findByIdForTransaction(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Recipient account not found: " + toAccountId));
        log.debug("Recipient account found: {} with balance: {}", to.getId(), to.getBalance());

        // Account check
        if (from.getId().equals(to.getId())) {
            log.warn("Attempted to transfer to the same account: {}", fromAccountId);
            throw new IdenticalAccountException("Sender and recipient account are the same: " + fromAccountId);
        }
        // Balance check
        if (from.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance in sender account: {} (current balance: {}, required: {})",
                    fromAccountId, from.getBalance(), amount);
            throw new InsufficientFundsException("Insufficient balance in sender account: " + fromAccountId);
        }

        // Record the transaction
        Transaction transaction = new Transaction(from.getId(), to.getId(), amount, Instant.now());
        // Save the transaction
        transactionRepo.save(transaction);
        log.debug("Transaction record saved with ID: {}", transaction.getId());

        // Deduct and credit
        from.setBalance(from.getBalance().subtract(amount));
        log.debug("Deducted {} from sender's balance, new balance: {}", amount, from.getBalance());

        to.setBalance(to.getBalance().add(amount));
        log.debug("Credited {} to recipient's balance, new balance: {}", amount, to.getBalance());

        // Save updated accounts
        accountRepo.save(from);
        accountRepo.save(to);
        log.info("Account balances updated. Sender: {}, Recipient: {}", from.getBalance(), to.getBalance());

        // Publish event for notification
        TransactionEvent event = new TransactionEvent(
                transaction.getId(),
                from.getId(),
                to.getId(),
                amount
        );
        kafkaTemplate.send("payments", event);
        log.info("Published Kafka event for transaction ID: {}", transaction.getId());

        log.info("Payment from '{}' to '{}' completed successfully (Transaction ID: {})",
                fromAccountId, toAccountId, transaction.getId());
        return transaction;
    }

    /**
     * Retrieves a transaction by its ID.
     *
     * @param id the ID of the transaction to retrieve
     * @return an Optional containing the Transaction if found, or empty if not found
     */
    public Optional<Transaction> getTransactionById(Long id) {
        log.debug("Fetching transaction by ID: {}", id);
        Optional<Transaction> tx = transactionRepo.findById(id);
        tx.ifPresentOrElse(
                transaction -> log.debug("Transaction found for ID: {}", id),
                () -> log.debug("No transaction found for ID: {}", id)
        );
        return tx;
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
        log.info("Circuit Breaker fallback triggered: {}", throwable.getMessage());

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
        log.info("Retry fallback triggered: {}", ex.getMessage());

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
        log.debug("Fetching balance for account: {}", accountId);
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        log.debug("Account found: {}, balance: {}", accountId, account.getBalance());
        return account.getBalance();
    }

}
