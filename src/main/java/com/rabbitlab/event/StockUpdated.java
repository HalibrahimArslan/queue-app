package com.rabbitlab.event;

/**
 * Stok miktarı değişti. MUTLAK değer taşır ("artık 8"), fark değil ("2 azaldı").
 * Böylece aynı mesaj iki kez gelse de sonuç değişmez. {@code version} stok versiyonudur.
 */
public record StockUpdated(String sku, int quantity, long version) implements ProductEvent {

    public StockUpdated {
        Require.notBlank(sku, "sku");
        Require.notNegative(quantity, "quantity");
        Require.positive(version, "version");
    }
}
