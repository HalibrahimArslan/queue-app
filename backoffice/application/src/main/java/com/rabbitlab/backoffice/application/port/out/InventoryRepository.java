package com.rabbitlab.backoffice.application.port.out;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.inventory.Inventory;

import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findBySku(Sku sku);

    /** @throws ConcurrentUpdateException stok okunduktan sonra başkası tarafından değiştirildiyse */
    void save(Inventory inventory);
}
