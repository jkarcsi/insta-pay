package com.kibitsolutions.instapay.integration;

import com.kibitsolutions.instapay.controller.TransferRequest;
import com.kibitsolutions.instapay.domain.model.Account;
import com.kibitsolutions.instapay.domain.model.Transaction;
import com.kibitsolutions.instapay.domain.repository.AccountRepository;
import com.kibitsolutions.instapay.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class PaymentIntegrationTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        // Create two test accounts
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
    void testCreateAndFetchAccount() {
        Account fetched = accountRepository.findById("acc1").orElse(null);
        assertNotNull(fetched);
        // Compare using compareTo (0 means equal)
        assertEquals(0, fetched.getBalance().compareTo(BigDecimal.valueOf(1000)));
    }

    @Test
    void testTransferAndGetTransaction() {
        String baseUrl = "http://localhost:" + port + "/api/payments";

        // 1. POST transfer endpoint testing
        TransferRequest request = new TransferRequest();
        request.setFromAcct("acc1");
        request.setToAcct("acc2");
        request.setAmount(BigDecimal.valueOf(200));

        ResponseEntity<Transaction> postResponse = restTemplate.postForEntity(
                baseUrl + "/transfer", request, Transaction.class);
        assertEquals(HttpStatus.OK, postResponse.getStatusCode());
        Transaction tx = postResponse.getBody();
        assertNotNull(tx);
        Long txId = tx.getId();
        assertNotNull(txId);

        // 2. GET transaction endpoint testing
        ResponseEntity<Transaction> getResponse = restTemplate.getForEntity(
                baseUrl + "/transactions/" + txId, Transaction.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        Transaction fetchedTx = getResponse.getBody();
        assertNotNull(fetchedTx);
        assertEquals("acc1", fetchedTx.getFromAccountId());
        assertEquals("acc2", fetchedTx.getToAccountId());
        assertEquals(0, fetchedTx.getAmount().compareTo(BigDecimal.valueOf(200)));
    }

    @Test
    void testGetAccountBalance_Success() {
        String baseUrl = "http://localhost:" + port + "/api/payments";
        ResponseEntity<BigDecimal> response = restTemplate.getForEntity(
                baseUrl + "/account/acc1/balance", BigDecimal.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        BigDecimal balance = response.getBody();
        assertNotNull(balance);
        assertEquals(0, balance.compareTo(BigDecimal.valueOf(1000)));
    }

    @Test
    void testGetAccountBalance_AccountNotFound() {
        String baseUrl = "http://localhost:" + port + "/api/payments";
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/account/nonexistent/balance", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
