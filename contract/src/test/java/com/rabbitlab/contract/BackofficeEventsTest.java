package com.rabbitlab.contract;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2-M2 — Mesaj sözleşmesi. Tip adları ve routing key'ler Faz 1 ile aynı: kablodaki format
 * değişmedi, sadece artık domain nesnelerinden ayrı bir yerde tanımlı.
 */
class BackofficeEventsTest {

    private static final ProductCreatedMessage CREATED =
            new ProductCreatedMessage("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100.00"), "TRY", 1);

    @Test
    void should_name_types_as_in_phase_1() {
        assertThat(BackofficeEvents.typeOf(CREATED)).isEqualTo("ProductCreated");
        assertThat(BackofficeEvents.typeOf(new ProductUpdatedMessage("SKU-1", "Kupa", null, BigDecimal.TEN, "TRY", 2)))
                .isEqualTo("ProductUpdated");
        assertThat(BackofficeEvents.typeOf(new ProductDeactivatedMessage("SKU-1", 3))).isEqualTo("ProductDeactivated");
        assertThat(BackofficeEvents.typeOf(new StockUpdatedMessage("SKU-1", 5, 1))).isEqualTo("StockUpdated");
    }

    @Test
    void should_route_product_and_stock_messages_with_topic_keys() {
        assertThat(BackofficeEvents.routingKeyOf(CREATED)).isEqualTo("product.created");
        assertThat(BackofficeEvents.routingKeyOf(new ProductDeactivatedMessage("SKU-1", 3)))
                .isEqualTo("product.deactivated");
        assertThat(BackofficeEvents.routingKeyOf(new StockUpdatedMessage("SKU-1", 5, 1))).isEqualTo("stock.updated");
    }

    @Test
    void should_resolve_message_class_from_type() {
        assertThat(BackofficeEvents.classOf("StockUpdated")).isEqualTo(StockUpdatedMessage.class);
        assertThatThrownBy(() -> BackofficeEvents.classOf("PriceChanged"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PriceChanged");
    }
}
