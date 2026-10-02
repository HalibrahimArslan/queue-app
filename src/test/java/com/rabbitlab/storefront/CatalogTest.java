package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductDeactivated;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Storefront kataloğunun kuralları — RabbitMQ'dan bağımsız, saf unit test.
 * Kabul kriterleri docs/scenario.md içindeki US1-US4'ten gelir.
 */
class CatalogTest {

    private final Catalog catalog = new Catalog();

    private static ProductCreated created(String sku) {
        return new ProductCreated(sku, "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1);
    }

    private static ProductUpdated updated(String sku, String price, long version) {
        return new ProductUpdated(sku, "Kupa", "Seramik kupa", new BigDecimal(price), "TRY", version);
    }

    private CatalogItem item(String sku) {
        return catalog.find(sku).orElseThrow();
    }

    @Nested
    class US1_NewProduct {

        @Test
        void should_add_active_out_of_stock_item_when_product_created() {
            catalog.apply(created("SKU-1"));

            CatalogItem item = item("SKU-1");
            assertThat(item.name()).isEqualTo("Kupa");
            assertThat(item.price()).isEqualByComparingTo("100");
            assertThat(item.active()).isTrue();
            assertThat(item.outOfStock()).isTrue();
        }

        @Test
        void should_keep_single_item_when_same_product_created_twice() {
            catalog.apply(created("SKU-1"));
            catalog.apply(created("SKU-1"));

            assertThat(catalog.visibleItems()).hasSize(1);
        }
    }

    @Nested
    class US2_ProductDetails {

        @Test
        void should_change_price_when_newer_update_arrives() {
            catalog.apply(created("SKU-1"));

            catalog.apply(updated("SKU-1", "120", 2));

            assertThat(item("SKU-1").price()).isEqualByComparingTo("120");
        }

        @Test
        void should_ignore_update_when_its_version_is_older() {
            catalog.apply(created("SKU-1"));
            catalog.apply(updated("SKU-1", "130", 3));

            catalog.apply(updated("SKU-1", "120", 2));

            assertThat(item("SKU-1").price()).isEqualByComparingTo("130");
        }

        @Test
        void should_reject_update_when_product_is_unknown() {
            assertThatThrownBy(() -> catalog.apply(updated("SKU-9", "120", 2)))
                    .isInstanceOf(UnknownProductException.class)
                    .hasMessageContaining("SKU-9");
        }
    }

    @Nested
    class US3_Stock {

        @Test
        void should_set_stock_when_stock_updated() {
            catalog.apply(created("SKU-1"));

            catalog.apply(new StockUpdated("SKU-1", 8, 1));

            assertThat(item("SKU-1").stock()).isEqualTo(8);
            assertThat(item("SKU-1").outOfStock()).isFalse();
        }

        @Test
        void should_keep_stock_when_same_message_arrives_twice() {
            catalog.apply(created("SKU-1"));
            StockUpdated message = new StockUpdated("SKU-1", 8, 2);

            catalog.apply(message);
            catalog.apply(message);

            assertThat(item("SKU-1").stock()).isEqualTo(8);
        }

        @Test
        void should_ignore_stock_when_older_message_arrives_late() {
            catalog.apply(created("SKU-1"));
            catalog.apply(new StockUpdated("SKU-1", 8, 3));

            catalog.apply(new StockUpdated("SKU-1", 10, 2));

            assertThat(item("SKU-1").stock()).isEqualTo(8);
        }

        @Test
        void should_show_out_of_stock_when_stock_drops_to_zero() {
            catalog.apply(created("SKU-1"));
            catalog.apply(new StockUpdated("SKU-1", 1, 1));

            catalog.apply(new StockUpdated("SKU-1", 0, 2));

            assertThat(item("SKU-1").outOfStock()).isTrue();
        }

        @Test
        void should_apply_stock_when_details_version_is_higher_than_stock_version() {
            // Neden iki ayrı versiyon? Fiyat güncellemesi v5 olsa bile stok v1 hâlâ yeni bir bilgidir.
            catalog.apply(created("SKU-1"));
            catalog.apply(updated("SKU-1", "120", 5));

            catalog.apply(new StockUpdated("SKU-1", 7, 1));

            assertThat(item("SKU-1").stock()).isEqualTo(7);
        }

        @Test
        void should_reject_stock_when_product_is_unknown() {
            assertThatThrownBy(() -> catalog.apply(new StockUpdated("SKU-9", 3, 1)))
                    .isInstanceOf(UnknownProductException.class);
        }
    }

    @Nested
    class US4_Deactivation {

        @Test
        void should_hide_item_when_product_deactivated() {
            catalog.apply(created("SKU-1"));

            catalog.apply(new ProductDeactivated("SKU-1", 2));

            assertThat(item("SKU-1").active()).isFalse();
            assertThat(catalog.visibleItems()).isEmpty();
        }

        @Test
        void should_stay_hidden_when_late_stock_update_arrives() {
            catalog.apply(created("SKU-1"));
            catalog.apply(new ProductDeactivated("SKU-1", 2));

            catalog.apply(new StockUpdated("SKU-1", 5, 1));

            assertThat(item("SKU-1").active()).isFalse();
            assertThat(item("SKU-1").stock()).isEqualTo(5);
        }

        @Test
        void should_stay_hidden_when_older_update_arrives_late() {
            catalog.apply(created("SKU-1"));
            catalog.apply(new ProductDeactivated("SKU-1", 3));

            catalog.apply(updated("SKU-1", "120", 2));

            assertThat(item("SKU-1").active()).isFalse();
            assertThat(item("SKU-1").price()).isEqualByComparingTo("100");
        }
    }
}
