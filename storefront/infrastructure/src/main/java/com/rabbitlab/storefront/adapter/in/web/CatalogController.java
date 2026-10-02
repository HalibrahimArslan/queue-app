package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vitrinin REST girişi (sadece okuma). Domain nesnesi doğrudan JSON'a çevrilmez; bir görünüm
 * record'u ({@link CatalogItemView}) araya girer. Böylece domain'e alan eklemek API'yi değiştirmez.
 */
@RestController
@RequestMapping("/catalog")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    record CatalogItemView(String sku, String name, String description, BigDecimal price, String currency,
                           int stock, boolean outOfStock, boolean active) {

        static CatalogItemView of(CatalogItem item) {
            return new CatalogItemView(item.sku().value(), item.name(), item.description(), item.price().amount(),
                    item.price().currency(), item.stock(), item.outOfStock(), item.active());
        }
    }

    /** Sitede görünen (aktif) ürünler. */
    @GetMapping
    List<CatalogItemView> visibleItems() {
        return catalog.visibleItems().stream().map(CatalogItemView::of).toList();
    }

    /** Tek ürün; pasif olsa da döner (vitrinde gizli ama kaydı var). */
    @GetMapping("/{sku}")
    ResponseEntity<CatalogItemView> item(@PathVariable String sku) {
        return ResponseEntity.of(catalog.find(new Sku(sku)).map(CatalogItemView::of));
    }
}
