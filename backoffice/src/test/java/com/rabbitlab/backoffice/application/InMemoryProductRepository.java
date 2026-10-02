package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.port.out.ProductRepository;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Product;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Port'un sahte (fake) uygulaması. Application katmanını veritabanı olmadan test edebiliyoruz:
 * hexagonal mimarinin en somut faydası.
 */
class InMemoryProductRepository implements ProductRepository {

    final Map<Sku, Product> saved = new HashMap<>();

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return Optional.ofNullable(saved.get(sku));
    }

    @Override
    public void save(Product product) {
        saved.put(product.sku(), product);
    }
}
