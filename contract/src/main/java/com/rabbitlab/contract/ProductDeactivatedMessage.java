package com.rabbitlab.contract;

/** Ürün satıştan kaldırıldı. {@code version}: ürün bilgisi versiyonu. */
public record ProductDeactivatedMessage(String sku, long version) implements BackofficeMessage {
}
