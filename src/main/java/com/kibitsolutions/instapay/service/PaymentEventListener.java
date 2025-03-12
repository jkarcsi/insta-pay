package com.kibitsolutions.instapay.service;

import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentEventListener {

    @KafkaListener(
            topics = "payments",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(TransactionEvent event) {
        // e.g.: email, SMS, log
        log.info("Received payment event: {}", event);
    }
}
