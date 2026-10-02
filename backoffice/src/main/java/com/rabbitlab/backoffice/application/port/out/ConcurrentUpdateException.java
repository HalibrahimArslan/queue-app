package com.rabbitlab.backoffice.application.port.out;

/**
 * Kaydedilmek istenen kopya eskimiş: okunduktan sonra başkası değiştirmiş (optimistic locking).
 * Çağıran taraf güncel hâli okuyup işlemi tekrar denemeli.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String aggregate, String id, long expectedVersion) {
        super("%s %s başkası tarafından değiştirildi (beklenen versiyon: %d)".formatted(aggregate, id, expectedVersion));
    }
}
