package com.rabbitlab.backoffice;

/** Mesajın broker'a güvenle ulaştığı doğrulanamadı. Çağıran taraf (Backoffice) bunu bilmeli. */
public class PublishFailedException extends RuntimeException {

    public PublishFailedException(String message) {
        super(message);
    }

    public PublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
