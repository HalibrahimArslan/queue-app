package com.rabbitlab.event;

/**
 * Backoffice'ten Storefront'a giden tüm event'lerin ortak tipi.
 *
 * <p>{@code sealed}: bu arayüzü sadece aşağıdaki dört record uygulayabilir. Böylece
 * {@code switch} ifadelerinde derleyici "bir event tipini unuttun" diye bizi uyarır.
 */
public sealed interface ProductEvent
        permits ProductCreated, ProductUpdated, StockUpdated, ProductDeactivated {

    String sku();

    long version();
}
