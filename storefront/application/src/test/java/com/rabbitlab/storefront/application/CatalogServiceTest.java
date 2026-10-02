package com.rabbitlab.storefront.application;

import com.rabbitlab.storefront.application.port.in.BrowseCatalogUseCase;
import com.rabbitlab.storefront.application.port.in.CatalogUpdate;
import com.rabbitlab.storefront.application.port.in.UpdateCatalogUseCase;
import com.rabbitlab.storefront.application.port.out.Transaction;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.UnknownProductException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2-M4 — Kataloğu güncelleyen use case. Girdi {@link CatalogUpdate}: Storefront'un kendi dilinde
 * "olanlar". Mesaj sözleşmesinden bu dile çeviriyi messaging adapter'ı yapacak (P2-M5).
 */
class CatalogServiceTest {

    private static final Sku SKU = new Sku("SKU-1");
    private static final Price PRICE = new Price(new BigDecimal("100"), "TRY");

    private final InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
    private final CatalogService service = new CatalogService(repository, new Transaction() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    });
    private final UpdateCatalogUseCase update = service;
    private final BrowseCatalogUseCase browse = service;

    @Test
    void should_show_new_product_as_visible_and_out_of_stock() {
        update.apply(new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        assertThat(browse.visibleItems()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo(SKU);
            assertThat(item.outOfStock()).isTrue();
        });
    }

    @Test
    void should_not_duplicate_product_when_registered_twice() { // US1
        update.apply(new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));
        update.apply(new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        assertThat(browse.visibleItems()).hasSize(1);
    }

    @Test
    void should_apply_stock_and_details_to_existing_product() {
        update.apply(new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        update.apply(new CatalogUpdate.StockChanged(SKU, 8, 1));
        update.apply(new CatalogUpdate.DetailsChanged(SKU, "Büyük kupa", null,
                new Price(new BigDecimal("120"), "TRY"), 2));

        assertThat(browse.find(SKU)).hasValueSatisfying(item -> {
            assertThat(item.stock()).isEqualTo(8);
            assertThat(item.name()).isEqualTo("Büyük kupa");
        });
    }

    @Test
    void should_hide_withdrawn_product() {
        update.apply(new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        update.apply(new CatalogUpdate.Withdrawn(SKU, 2));

        assertThat(browse.visibleItems()).isEmpty();
        assertThat(browse.find(SKU)).isPresent();
    }

    @Test
    void should_fail_for_unknown_product_so_that_it_can_be_retried() { // US5-2
        // Ürünün kendisi henüz gelmemiş olabilir (iki kuyruk arasında sıra garantisi yok).
        assertThatThrownBy(() -> update.apply(new CatalogUpdate.StockChanged(new Sku("SKU-9"), 4, 1)))
                .isInstanceOf(UnknownProductException.class)
                .hasMessageContaining("SKU-9");
    }
}
