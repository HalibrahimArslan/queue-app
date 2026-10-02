package com.rabbitlab.storefront.application;

import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.CatalogRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryCatalogRepository implements CatalogRepository {

    final Map<Sku, CatalogItem> items = new HashMap<>();

    @Override
    public Optional<CatalogItem> find(Sku sku) {
        return Optional.ofNullable(items.get(sku));
    }

    @Override
    public Optional<CatalogItem> findForUpdate(Sku sku) {
        return find(sku);
    }

    @Override
    public List<CatalogItem> findVisible() {
        return items.values().stream().filter(CatalogItem::active)
                .sorted(Comparator.comparing(item -> item.sku().value())).toList();
    }

    @Override
    public boolean insertIfAbsent(CatalogItem item) {
        return items.putIfAbsent(item.sku(), item) == null;
    }

    @Override
    public void update(CatalogItem item) {
        items.put(item.sku(), item);
    }
}
