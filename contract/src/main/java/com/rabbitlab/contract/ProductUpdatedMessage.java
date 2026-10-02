package com.rabbitlab.contract;

import java.math.BigDecimal;

/** Ad, açıklama veya fiyat değişti. {@code version}: ürün bilgisi versiyonu. */
public record ProductUpdatedMessage(
        String sku, String name, String description, BigDecimal price, String currency, long version)
        implements BackofficeMessage {
}
