package com.kibitsolutions.instapay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class InstaPayApplication {
    public static void main(String[] args) {
        SpringApplication.run(InstaPayApplication.class, args);
    }
}
