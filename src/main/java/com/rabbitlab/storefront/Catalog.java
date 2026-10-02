package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductDeactivated;
import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Storefront'un vitrini. Faz 1'de bellekte tutulur (Faz 2'de PostgreSQL olacak).
 *
 * <p>Thread-safe: birden fazla consumer aynı anda yazabilir (M2). {@code compute} aynı SKU için
 * güncellemeleri atomik yapar.
 */
public final class Catalog {

    private final ConcurrentMap<String, CatalogItem> items = new ConcurrentHashMap<>();

    public void apply(ProductEvent event) {
        switch (event) {
            case ProductCreated created -> items.putIfAbsent(created.sku(), CatalogItem.from(created));
            case ProductUpdated updated -> items.compute(updated.sku(), (sku, item) -> existing(sku, item).apply(updated));
            case StockUpdated stock -> items.compute(stock.sku(), (sku, item) -> existing(sku, item).apply(stock));
            case ProductDeactivated deactivated ->
                    items.compute(deactivated.sku(), (sku, item) -> existing(sku, item).apply(deactivated));
        }
    }

    public Optional<CatalogItem> find(String sku) {
        return Optional.ofNullable(items.get(sku));
    }

    /** Sitede görünen (aktif) ürünler. */
    public List<CatalogItem> visibleItems() {
        return items.values().stream()
                .filter(CatalogItem::active)
                .sorted(Comparator.comparing(CatalogItem::sku))
                .toList();
    }

    private static CatalogItem existing(String sku, CatalogItem item) {
        if (item == null) {
            throw new UnknownProductException(sku);
        }
        return item;
    }
}
