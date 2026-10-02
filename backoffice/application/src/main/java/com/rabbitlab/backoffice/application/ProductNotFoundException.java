package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.Sku;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Sku sku) {
        super("Ürün bulunamadı: " + sku);
    }
}
