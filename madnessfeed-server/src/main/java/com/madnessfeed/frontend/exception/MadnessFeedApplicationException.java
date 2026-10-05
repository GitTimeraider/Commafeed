package com.madnessfeed.frontend.exception;

import java.io.Serial;
import java.util.Objects;

public class MadnessFeedApplicationException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    private final MadnessFeedExceptionType type;

    public MadnessFeedApplicationException(MadnessFeedExceptionType type) {
        super(Objects.requireNonNull(type).message());
        this.type = type;
    }

    public MadnessFeedExceptionType type() {
        return type;
    }
}
