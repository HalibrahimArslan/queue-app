package com.rabbitlab.event;

import java.math.BigDecimal;

/** Backoffice'te yeni ürün oluşturuldu. Stok taşımaz; ürün "Tükendi" olarak doğar. */
public record ProductCreated(
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        long version) implements ProductEvent {

    public ProductCreated {
        Require.notBlank(sku, "sku");
        Require.notBlank(name, "name");
        Require.positive(price, "price");
        Require.notBlank(currency, "currency");
        Require.positive(version, "version");
    }
}
