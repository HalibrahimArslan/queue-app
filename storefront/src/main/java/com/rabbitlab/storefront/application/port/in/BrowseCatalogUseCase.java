package com.rabbitlab.storefront.application.port.in;

import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;

import java.util.List;
import java.util.Optional;

/** Vitrini okumak. */
public interface BrowseCatalogUseCase {

    Optional<CatalogItem> find(Sku sku);

    /** Sitede görünen (aktif) ürünler, SKU sırasıyla. */
    List<CatalogItem> visibleItems();
}
