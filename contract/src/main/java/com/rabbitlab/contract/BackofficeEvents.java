package com.rabbitlab.contract;

import java.util.Map;

/**
 * Sözleşmenin "zarf" kısmı: exchange adı, mesaj tipleri ve routing key'ler.
 *
 * <ul>
 *   <li>Mesaj tipi AMQP {@code type} özelliğinde taşınır (Faz 1 ile aynı).</li>
 *   <li>Routing key formatı {@code <konu>.<olay>}; Storefront {@code product.*} ve {@code stock.*} ile abone olur.</li>
 * </ul>
 */
public final class BackofficeEvents {

    public static final String EXCHANGE = "backoffice.events";
    public static final String PRODUCT_EVENTS = "product.*";
    public static final String STOCK_EVENTS = "stock.*";

    private static final Map<String, Class<? extends BackofficeMessage>> TYPES = Map.of(
            "ProductCreated", ProductCreatedMessage.class,
            "ProductUpdated", ProductUpdatedMessage.class,
            "ProductDeactivated", ProductDeactivatedMessage.class,
            "StockUpdated", StockUpdatedMessage.class);

    private BackofficeEvents() {
    }

    public static String typeOf(BackofficeMessage message) {
        return switch (message) {
            case ProductCreatedMessage ignored -> "ProductCreated";
            case ProductUpdatedMessage ignored -> "ProductUpdated";
            case ProductDeactivatedMessage ignored -> "ProductDeactivated";
            case StockUpdatedMessage ignored -> "StockUpdated";
        };
    }

    public static String routingKeyOf(BackofficeMessage message) {
        return switch (message) {
            case ProductCreatedMessage ignored -> "product.created";
            case ProductUpdatedMessage ignored -> "product.updated";
            case ProductDeactivatedMessage ignored -> "product.deactivated";
            case StockUpdatedMessage ignored -> "stock.updated";
        };
    }

    public static Class<? extends BackofficeMessage> classOf(String type) {
        Class<? extends BackofficeMessage> messageClass = type == null ? null : TYPES.get(type);
        if (messageClass == null) {
            throw new IllegalArgumentException("Bilinmeyen mesaj tipi: " + type);
        }
        return messageClass;
    }
}
