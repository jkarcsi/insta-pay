package com.kibitsolutions.instapay.config;

import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;

    public DataInitializer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) {
        if (!accountRepository.existsById("acc1")) {
            Account account1 = new Account();
            account1.setId("acc1");
            account1.setBalance(BigDecimal.valueOf(1000));
            accountRepository.save(account1);
        }
        if (!accountRepository.existsById("acc2")) {
            Account account2 = new Account();
            account2.setId("acc2");
            account2.setBalance(BigDecimal.valueOf(500));
            accountRepository.save(account2);
        }
    }
}
