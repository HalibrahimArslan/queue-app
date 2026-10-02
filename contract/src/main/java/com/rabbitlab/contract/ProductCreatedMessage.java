package com.rabbitlab.contract;

import java.math.BigDecimal;

/** Yeni ürün. Stok taşımaz; ürün "Tükendi" doğar. {@code version}: ürün bilgisi versiyonu. */
public record ProductCreatedMessage(
        String sku, String name, String description, BigDecimal price, String currency, long version)
        implements BackofficeMessage {
}
