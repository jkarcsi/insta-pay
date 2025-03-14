package com.kibitsolutions.instapay.exception;

public class IdenticalAccountException extends RuntimeException {
    public IdenticalAccountException(String message) {
        super(message);
    }
}
