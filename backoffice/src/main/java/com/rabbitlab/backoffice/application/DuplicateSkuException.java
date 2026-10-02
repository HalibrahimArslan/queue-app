package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.Sku;

/** SKU benzersizdir. */
public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(Sku sku) {
        super("Bu SKU ile ürün zaten var: " + sku);
    }
}
