package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.port.in.CreateProductCommand;
import com.rabbitlab.backoffice.application.port.in.CreateProductUseCase;
import com.rabbitlab.backoffice.application.port.in.DeactivateProductUseCase;
import com.rabbitlab.backoffice.application.port.in.UpdateProductCommand;
import com.rabbitlab.backoffice.application.port.in.UpdateProductUseCase;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.event.ProductCreated;
import com.rabbitlab.backoffice.domain.event.ProductDeactivated;
import com.rabbitlab.backoffice.domain.event.ProductUpdated;
import com.rabbitlab.backoffice.domain.product.Price;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2-M2 — Ürün use case'leri. Test, servisi somut sınıfı üzerinden değil inbound port'ları
 * (use case arayüzleri) üzerinden kullanıyor; REST adapter'ı da (M6) aynısını yapacak.
 */
class ProductServiceTest {

    private static final Sku SKU = new Sku("SKU-1");
    private static final Price PRICE = new Price(new BigDecimal("100"), "TRY");

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryInventoryRepository inventories = new InMemoryInventoryRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final ProductService service = new ProductService(products, inventories, outbox, new DirectTransaction());

    private final CreateProductUseCase create = service;
    private final UpdateProductUseCase update = service;
    private final DeactivateProductUseCase deactivate = service;

    @Test
    void should_save_product_with_empty_inventory_and_write_event_to_outbox() {
        create.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));

        assertThat(products.saved).containsKey(SKU);
        assertThat(inventories.saved.get(SKU).quantity()).isZero();
        assertThat(outbox.events).containsExactly(new ProductCreated(SKU, "Kupa", "Seramik kupa", PRICE, 1));
    }

    @Test
    void should_reject_duplicate_sku_without_writing_event() {
        create.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));
        outbox.events.clear();

        assertThatThrownBy(() -> create.create(new CreateProductCommand(SKU, "Başka", null, PRICE)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("SKU-1");
        assertThat(outbox.events).isEmpty();
        assertThat(products.saved.get(SKU).name()).isEqualTo("Kupa");
    }

    @Test
    void should_write_updated_event_when_details_change() {
        create.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));
        outbox.events.clear();
        Price newPrice = new Price(new BigDecimal("120"), "TRY");

        update.update(new UpdateProductCommand(SKU, "Kupa", "Seramik kupa", newPrice));

        assertThat(outbox.events).containsExactly(new ProductUpdated(SKU, "Kupa", "Seramik kupa", newPrice, 2));
    }

    @Test
    void should_write_deactivated_event() {
        create.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));
        outbox.events.clear();

        deactivate.deactivate(SKU);

        assertThat(outbox.events).containsExactly(new ProductDeactivated(SKU, 2));
    }

    @Test
    void should_fail_when_updating_unknown_product() {
        assertThatThrownBy(() -> update.update(new UpdateProductCommand(SKU, "Kupa", null, PRICE)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("SKU-1");
    }

    @Test
    void should_fail_when_deactivating_unknown_product() {
        assertThatThrownBy(() -> deactivate.deactivate(SKU)).isInstanceOf(ProductNotFoundException.class);
    }
}
