package com.rabbitlab.contract;

/** Stok MUTLAK değerle ("artık 8"). {@code version}: stok versiyonu (ürün versiyonundan bağımsız). */
public record StockUpdatedMessage(String sku, int quantity, long version) implements BackofficeMessage {
}
