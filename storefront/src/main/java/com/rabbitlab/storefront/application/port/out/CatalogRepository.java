package com.rabbitlab.storefront.application.port.out;

import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;

import java.util.List;
import java.util.Optional;

public interface CatalogRepository {

    Optional<CatalogItem> find(Sku sku);

    /**
     * Ürünü okur ve transaction bitene kadar kilitler. Aynı ürünü güncellemek isteyen diğer
     * consumer'lar sırada bekler; böylece kimse diğerinin değişikliğini ezmez.
     */
    Optional<CatalogItem> findForUpdate(Sku sku);

    List<CatalogItem> findVisible();

    /** @return eklendiyse {@code true}; aynı SKU zaten varsa hiçbir şey yapmaz ve {@code false} döner */
    boolean insertIfAbsent(CatalogItem item);

    void update(CatalogItem item);
}
