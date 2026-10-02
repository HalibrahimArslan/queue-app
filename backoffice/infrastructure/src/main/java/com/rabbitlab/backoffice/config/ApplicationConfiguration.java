package com.rabbitlab.backoffice.config;

import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.application.port.out.EventOutbox;
import com.rabbitlab.backoffice.application.port.out.InventoryRepository;
import com.rabbitlab.backoffice.application.port.out.ProductRepository;
import com.rabbitlab.backoffice.application.port.out.Transaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring: application servisleri Spring annotation'ı taşımıyor (framework'süz kalsınlar diye);
 * onları burada elle bean yapıyoruz. Adapter'lar ise {@code @Component} ile kendiliğinden bulunuyor.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    ProductService productService(ProductRepository products, InventoryRepository inventories, EventOutbox outbox,
                                  Transaction transaction) {
        return new ProductService(products, inventories, outbox, transaction);
    }

    @Bean
    InventoryService inventoryService(InventoryRepository inventories, EventOutbox outbox, Transaction transaction) {
        return new InventoryService(inventories, outbox, transaction);
    }
}
