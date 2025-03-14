package com.kibitsolutions.instapay.controller;

import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.exception.ResourceNotFoundException;
import com.kibitsolutions.instapay.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Controller")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
            summary = "Transfer money between accounts",
            description = "Transfers a certain amount from one account to another, if balance is sufficient."
    )
    @ApiResponse(responseCode = "200", description = "Transfer successful")
    @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid request data")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transfer(@RequestBody @Valid TransferRequest req) {
        Transaction tx = paymentService.processPayment(
                req.getFromAcct(),
                req.getToAcct(),
                req.getAmount()
        );
        return ResponseEntity.ok(tx);
    }

    @Operation(
            summary = "Get transaction by ID",
            description = "Retrieves details of a specific transaction by its ID."
    )
    @ApiResponse(responseCode = "200", description = "Transaction found")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    @GetMapping("/transactions/{id}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long id) {
        Transaction tx = paymentService.getTransactionById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return ResponseEntity.ok(tx);
    }

    @Operation(
            summary = "Get balance by account ID",
            description = "Retrieves the balance of a specific account by account ID."
    )
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/account/{accountId}/balance")
    public ResponseEntity<BigDecimal> getAccountBalance(@PathVariable String accountId) {
        BigDecimal balance = paymentService.getAccountBalance(accountId);
        return ResponseEntity.ok(balance);
    }
}
