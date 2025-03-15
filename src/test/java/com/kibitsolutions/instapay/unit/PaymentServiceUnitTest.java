package com.kibitsolutions.instapay.unit;

import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import com.kibitsolutions.instapay.exception.AccountNotFoundException;
import com.kibitsolutions.instapay.exception.InsufficientFundsException;
import com.kibitsolutions.instapay.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceUnitTest {

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private PaymentService paymentService;

    @BeforeEach
    public void setup() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);

        paymentService = new PaymentService(accountRepository, transactionRepository, kafkaTemplate);
    }

    @Test
    void testProcessPayment_SufficientBalance() {
        Account sender = new Account();
        sender.setId("acc1");
        sender.setBalance(BigDecimal.valueOf(1000));
        Account recipient = new Account();
        recipient.setId("acc2");
        recipient.setBalance(BigDecimal.valueOf(500));

        when(accountRepository.findByIdForTransaction("acc1")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForTransaction("acc2")).thenReturn(Optional.of(recipient));
        // Simulating save transaction: setting ID
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        Transaction result = paymentService.processPayment("acc1", "acc2", BigDecimal.valueOf(200));

        // Check if balance modified properly
        assertEquals(BigDecimal.valueOf(800), sender.getBalance());
        assertEquals(BigDecimal.valueOf(700), recipient.getBalance());
        // Check transaction details
        assertNotNull(result);
        assertEquals("acc1", result.getFromAccountId());
        assertEquals("acc2", result.getToAccountId());

        // Verify that KafkaTemplate send method has been called and the event is set correctly
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(kafkaTemplate, times(1)).send(eq("payments"), captor.capture());
        TransactionEvent event = captor.getValue();
        assertEquals(result.getId(), event.getTransactionId());
    }

    @Test
    void testProcessPayment_InsufficientBalance() {
        Account sender = new Account();
        sender.setId("acc1");
        sender.setBalance(BigDecimal.valueOf(100));
        Account recipient = new Account();
        recipient.setId("acc2");
        recipient.setBalance(BigDecimal.valueOf(500));

        when(accountRepository.findByIdForTransaction("acc1")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForTransaction("acc2")).thenReturn(Optional.of(recipient));

        assertThrows(InsufficientFundsException.class,
                () -> paymentService.processPayment("acc1", "acc2", BigDecimal.valueOf(200)));
    }

    @Test
    void testProcessPayment_SenderNotFound() {
        when(accountRepository.findByIdForTransaction("acc1")).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class,
                () -> paymentService.processPayment("acc1", "acc2", BigDecimal.valueOf(100)));
    }

    @Test
    void testGetAccountBalance_AccountFound() {
        Account account = new Account();
        account.setId("acc1");
        account.setBalance(BigDecimal.valueOf(500));

        when(accountRepository.findById("acc1"))
                .thenReturn(Optional.of(account));

        BigDecimal result = paymentService.getAccountBalance("acc1");

        assertEquals(BigDecimal.valueOf(500), result);
        verify(accountRepository, times(1)).findById("acc1");
    }

    @Test
    void testGetAccountBalance_AccountNotFound() {
        when(accountRepository.findById("acc999"))
                .thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> paymentService.getAccountBalance("acc999"));
    }
}
