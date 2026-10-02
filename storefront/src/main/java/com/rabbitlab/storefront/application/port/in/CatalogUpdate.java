package com.rabbitlab.storefront.application.port.in;

import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;

/**
 * Kataloğa yansıtılacak bir değişiklik, Storefront'un kendi dilinde.
 *
 * <p>Mesaj sözleşmesindeki {@code ProductDeactivatedMessage} burada {@link Withdrawn} ("vitrinden
 * kaldırıldı"); çeviriyi messaging adapter'ı yapar. Sözleşme değişirse sadece adapter değişir.
 */
public sealed interface CatalogUpdate {

    Sku sku();

    record NewProduct(Sku sku, String name, String description, Price price, long version)
            implements CatalogUpdate {
    }

    record DetailsChanged(Sku sku, String name, String description, Price price, long version)
            implements CatalogUpdate {
    }

    record StockChanged(Sku sku, int quantity, long version) implements CatalogUpdate {
    }

    record Withdrawn(Sku sku, long version) implements CatalogUpdate {
    }
}
