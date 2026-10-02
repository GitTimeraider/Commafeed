package com.commafeed.frontend.exception;

import org.jboss.resteasy.reactive.RestResponse.Status;

public enum CommaFeedExceptionType {
    WRONG_USERNAME_OR_PASSWORD(Status.UNAUTHORIZED, "wrong username or password"),
    MFA_REQUIRED(Status.UNAUTHORIZED, "two-factor authentication required"),
    MFA_INVALID_CODE(Status.UNAUTHORIZED, "invalid two-factor authentication code"),
    MFA_TOO_MANY_ATTEMPTS(Status.UNAUTHORIZED, "too many failed attempts, please try again later");

    private final Status status;
    private final String message;

    CommaFeedExceptionType(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }
}
