package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.port.out.InventoryRepository;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class InMemoryInventoryRepository implements InventoryRepository {

    final Map<Sku, Inventory> saved = new HashMap<>();

    @Override
    public Optional<Inventory> findBySku(Sku sku) {
        return Optional.ofNullable(saved.get(sku));
    }

    @Override
    public void save(Inventory inventory) {
        saved.put(inventory.sku(), inventory);
    }
}
