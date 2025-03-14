package com.kibitsolutions.instapay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

@Configuration
@Slf4j
public class RetryListenerConfig {

    @Bean
    public RetryListener paymentRetryListener() {
        return new PaymentRetryListener();
    }

    static class PaymentRetryListener implements RetryListener {

        @Override
        public <T, E extends Throwable> boolean open(
                RetryContext context,
                RetryCallback<T, E> callback
        ) {
            log.info("Payment process is about to start.");
            return true;
        }

        @Override
        public <T, E extends Throwable> void onError(
                RetryContext context,
                RetryCallback<T, E> callback,
                Throwable throwable
        ) {
            int retryCount = context.getRetryCount();
            log.error("Payment retry attempt #{} failed with exception: {}", retryCount, throwable.getMessage());
        }

        @Override
        public <T, E extends Throwable> void close(
                RetryContext context,
                RetryCallback<T, E> callback,
                Throwable throwable
        ) {
            if (throwable == null) {
                log.info("The payment process concluded successfully.");
            } else {
                log.error("The payment retry process ended after exhausting attempts. Final error: {}",
                        throwable.getMessage());
            }
        }
    }
}
