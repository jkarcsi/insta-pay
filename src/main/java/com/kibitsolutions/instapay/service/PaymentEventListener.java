package com.kibitsolutions.instapay.service;

import com.kibitsolutions.instapay.domain.model.TransactionEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventListener {

    @KafkaListener(
            topics = "payments",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(TransactionEvent event) {
        // e.g.: email, SMS, log
        System.out.println("Received payment event: " + event);
    }
}
