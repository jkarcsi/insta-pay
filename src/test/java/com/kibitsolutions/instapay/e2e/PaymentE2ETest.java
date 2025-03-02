package com.kibitsolutions.instapay.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kibitsolutions.instapay.controller.TransferRequest;
import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class PaymentE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Container
    public static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.3.1"));

    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // Creating two test accounts
        Account acc1 = new Account();
        acc1.setId("acc1");
        acc1.setBalance(BigDecimal.valueOf(1000));
        accountRepository.save(acc1);

        Account acc2 = new Account();
        acc2.setId("acc2");
        acc2.setBalance(BigDecimal.valueOf(500));
        accountRepository.save(acc2);
    }

    @Test
    void testTransferEndpoint_E2E() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setFromAcct("acc1");
        request.setToAcct("acc2");
        request.setAmount(BigDecimal.valueOf(200));

        String jsonRequest = objectMapper.writeValueAsString(request);

        // Call the POST transfer endpoint
        String responseJson = mockMvc.perform(post("/api/payments/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Transaction transaction = objectMapper.readValue(responseJson, Transaction.class);
        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getFromAccountId()).isEqualTo("acc1");
        assertThat(transaction.getToAccountId()).isEqualTo("acc2");
        assertThat(transaction.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));

        // Call the GET transaction endpoint
        String getResponseJson = mockMvc.perform(get("/api/payments/transactions/" + transaction.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Transaction fetchedTx = objectMapper.readValue(getResponseJson, Transaction.class);
        assertThat(fetchedTx.getId()).isEqualTo(transaction.getId());
    }

    @Test
    void testGetAccountBalance_E2E_Success() throws Exception {
        // Call the GET balance endpoint for an existing account
        String balanceResponse = mockMvc.perform(get("/api/payments/account/acc1/balance"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        BigDecimal balance = objectMapper.readValue(balanceResponse, BigDecimal.class);
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void testGetAccountBalance_E2E_AccountNotFound() throws Exception {
        // Call the GET balance endpoint for a non-existing account
        mockMvc.perform(get("/api/payments/account/nonexistent/balance"))
                .andExpect(status().isNotFound());
    }
}
