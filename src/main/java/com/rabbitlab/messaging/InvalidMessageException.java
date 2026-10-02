package com.rabbitlab.messaging;

/** Mesaj okunamıyor ya da kurallara aykırı. Tekrar denemek işe yaramaz (poison message). */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
