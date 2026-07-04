package com.streamhelper.app.ai;

public class AiClientException extends RuntimeException {
    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
