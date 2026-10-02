package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.port.in.CountStockCommand;
import com.rabbitlab.backoffice.application.port.in.CountStockUseCase;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.event.StockUpdated;
import com.rabbitlab.backoffice.domain.inventory.Inventory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2-M2 — Stok sayımı use case'i. */
class InventoryServiceTest {

    private static final Sku SKU = new Sku("SKU-1");

    private final InMemoryInventoryRepository inventories = new InMemoryInventoryRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final CountStockUseCase countStock = new InventoryService(inventories, outbox, new DirectTransaction());

    @Test
    void should_save_count_and_write_stock_event() {
        inventories.save(Inventory.open(SKU));

        countStock.count(new CountStockCommand(SKU, 8));

        assertThat(inventories.saved.get(SKU).quantity()).isEqualTo(8);
        assertThat(outbox.events).containsExactly(new StockUpdated(SKU, 8, 1));
    }

    @Test
    void should_fail_when_counting_stock_of_unknown_product() {
        // Inventory, ürün oluşturulurken açılır; inventory yoksa ürün de yoktur.
        assertThatThrownBy(() -> countStock.count(new CountStockCommand(SKU, 8)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThat(outbox.events).isEmpty();
    }
}
