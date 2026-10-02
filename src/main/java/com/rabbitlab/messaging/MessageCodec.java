package com.rabbitlab.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductDeactivated;
import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;

import java.io.IOException;
import java.util.Map;

/**
 * Event ↔ byte[] dönüşümü. Gövde JSON; tip bilgisi AMQP "type" özelliğinde taşınır.
 */
public final class MessageCodec {

    private static final Map<String, Class<? extends ProductEvent>> TYPES = Map.of(
            "ProductCreated", ProductCreated.class,
            "ProductUpdated", ProductUpdated.class,
            "StockUpdated", StockUpdated.class,
            "ProductDeactivated", ProductDeactivated.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public String typeOf(ProductEvent event) {
        return event.getClass().getSimpleName();
    }

    public byte[] encode(ProductEvent event) {
        try {
            return mapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event JSON'a çevrilemedi: " + event, e);
        }
    }

    public ProductEvent decode(String type, byte[] body) {
        Class<? extends ProductEvent> eventClass = type == null ? null : TYPES.get(type);
        if (eventClass == null) {
            throw new InvalidMessageException("Bilinmeyen mesaj tipi: " + type);
        }
        try {
            return mapper.readValue(body, eventClass);
        } catch (IOException | IllegalArgumentException e) {
            // Record'un kurucusundaki doğrulama hatası da (ör. negatif stok) buraya düşer.
            throw new InvalidMessageException("Mesaj okunamadı (" + type + "): " + e.getMessage(), e);
        }
    }
}
