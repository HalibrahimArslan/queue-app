package com.rabbitlab.event;

import java.math.BigDecimal;

/** Ürünün adı, açıklaması veya fiyatı değişti. Ürün bilgisi versiyonunu taşır. */
public record ProductUpdated(
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        long version) implements ProductEvent {

    public ProductUpdated {
        Require.notBlank(sku, "sku");
        Require.notBlank(name, "name");
        Require.positive(price, "price");
        Require.notBlank(currency, "currency");
        Require.positive(version, "version");
    }
}
