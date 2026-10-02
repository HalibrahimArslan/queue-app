package com.rabbitlab.backoffice.application.port.out;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Product;

import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findBySku(Sku sku);

    /**
     * Yeni ürünü ekler veya var olanı günceller.
     *
     * @throws ConcurrentUpdateException ürün okunduktan sonra başkası tarafından değiştirildiyse
     */
    void save(Product product);
}
