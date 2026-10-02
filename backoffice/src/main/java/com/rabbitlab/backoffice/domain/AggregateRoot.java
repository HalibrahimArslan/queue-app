package com.rabbitlab.backoffice.domain;

import com.rabbitlab.backoffice.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate'lerin ortak tabanı: olan biteni domain event'i olarak biriktirir.
 *
 * <p>Aggregate event'i kendisi yayınlamaz (RabbitMQ'yu bilmez). Application katmanı aggregate'i
 * kaydederken {@link #pullEvents()} ile event'leri alır ve outbox'a yazar (P2-M2).
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> events = new ArrayList<>();

    protected void record(DomainEvent event) {
        events.add(event);
    }

    /** Biriken event'leri verir ve listeyi boşaltır; aynı event iki kez alınmaz. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
