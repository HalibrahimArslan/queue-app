package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductDeactivated;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;

import java.math.BigDecimal;

/**
 * Storefront'taki ürün kopyası. Immutable: her değişiklik yeni bir nesne döner.
 *
 * <p>İki ayrı versiyon tutar: ürün bilgisi ({@code detailsVersion}) ve stok ({@code stockVersion}).
 * Gelen event'in versiyonu mevcut versiyondan büyük değilse event yok sayılır; böylece hem
 * tekrar gelen hem de geç gelen eski mesajlar zararsız olur.
 */
public record CatalogItem(
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int stock,
        boolean active,
        long detailsVersion,
        long stockVersion) {

    static CatalogItem from(ProductCreated event) {
        return new CatalogItem(event.sku(), event.name(), event.description(), event.price(), event.currency(),
                0, true, event.version(), 0);
    }

    CatalogItem apply(ProductUpdated event) {
        if (event.version() <= detailsVersion) {
            return this;
        }
        return new CatalogItem(sku, event.name(), event.description(), event.price(), event.currency(),
                stock, active, event.version(), stockVersion);
    }

    CatalogItem apply(StockUpdated event) {
        if (event.version() <= stockVersion) {
            return this;
        }
        return new CatalogItem(sku, name, description, price, currency,
                event.quantity(), active, detailsVersion, event.version());
    }

    CatalogItem apply(ProductDeactivated event) {
        if (event.version() <= detailsVersion) {
            return this;
        }
        return new CatalogItem(sku, name, description, price, currency,
                stock, false, event.version(), stockVersion);
    }

    public boolean outOfStock() {
        return stock == 0;
    }
}
