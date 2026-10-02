package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.port.in.CountStockCommand;
import com.rabbitlab.backoffice.application.port.in.CountStockUseCase;
import com.rabbitlab.backoffice.application.port.out.EventOutbox;
import com.rabbitlab.backoffice.application.port.out.InventoryRepository;
import com.rabbitlab.backoffice.application.port.out.Transaction;
import com.rabbitlab.backoffice.domain.inventory.Inventory;

/**
 * Stok use case'i. Product'ı hiç yüklemez: ayrı aggregate olmanın faydası, fiyat güncellemesi ile
 * stok sayımı aynı satırı kilitlemez.
 */
public final class InventoryService implements CountStockUseCase {

    private final InventoryRepository inventories;
    private final EventOutbox outbox;
    private final Transaction transaction;

    public InventoryService(InventoryRepository inventories, EventOutbox outbox, Transaction transaction) {
        this.inventories = inventories;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    @Override
    public void count(CountStockCommand command) {
        transaction.execute(() -> {
            // Inventory, ürünle birlikte açılır; inventory yoksa ürün de yoktur.
            Inventory inventory = inventories.findBySku(command.sku())
                    .orElseThrow(() -> new ProductNotFoundException(command.sku()));
            inventory.count(command.quantity());
            var events = inventory.pullEvents();
            if (events.isEmpty()) {
                return;
            }
            inventories.save(inventory);
            outbox.append(events);
        });
    }
}
