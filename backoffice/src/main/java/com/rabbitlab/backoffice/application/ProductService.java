package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.port.in.CreateProductCommand;
import com.rabbitlab.backoffice.application.port.in.CreateProductUseCase;
import com.rabbitlab.backoffice.application.port.in.DeactivateProductUseCase;
import com.rabbitlab.backoffice.application.port.in.UpdateProductCommand;
import com.rabbitlab.backoffice.application.port.in.UpdateProductUseCase;
import com.rabbitlab.backoffice.application.port.out.EventOutbox;
import com.rabbitlab.backoffice.application.port.out.InventoryRepository;
import com.rabbitlab.backoffice.application.port.out.ProductRepository;
import com.rabbitlab.backoffice.application.port.out.Transaction;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.inventory.Inventory;
import com.rabbitlab.backoffice.domain.product.Product;

/**
 * Ürün use case'leri. Her metot aynı kalıbı izler: yükle → domain'e sor → kaydet → event'leri outbox'a yaz.
 * Kurallar domain'de; servis sadece akışı yönetir (orkestrasyon).
 */
public final class ProductService implements CreateProductUseCase, UpdateProductUseCase, DeactivateProductUseCase {

    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final EventOutbox outbox;
    private final Transaction transaction;

    public ProductService(ProductRepository products, InventoryRepository inventories, EventOutbox outbox,
                          Transaction transaction) {
        this.products = products;
        this.inventories = inventories;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    @Override
    public void create(CreateProductCommand command) {
        transaction.execute(() -> {
            if (products.findBySku(command.sku()).isPresent()) {
                throw new DuplicateSkuException(command.sku());
            }
            Product product = Product.create(command.sku(), command.name(), command.description(), command.price());
            // Ürün ve stoğu aynı anda açılır: "ürünü olan ama stok kaydı olmayan" bir durum hiç oluşmaz.
            Inventory inventory = Inventory.open(command.sku());
            products.save(product);
            inventories.save(inventory);
            outbox.append(product.pullEvents());
        });
    }

    @Override
    public void update(UpdateProductCommand command) {
        transaction.execute(() -> {
            Product product = existing(command.sku());
            product.update(command.name(), command.description(), command.price());
            save(product);
        });
    }

    @Override
    public void deactivate(Sku sku) {
        transaction.execute(() -> {
            Product product = existing(sku);
            product.deactivate();
            save(product);
        });
    }

    private Product existing(Sku sku) {
        return products.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    private void save(Product product) {
        var events = product.pullEvents();
        if (events.isEmpty()) {
            return; // değişiklik yok
        }
        products.save(product);
        outbox.append(events);
    }
}
