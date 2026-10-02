package com.rabbitlab.m1;

import com.rabbitlab.backoffice.BackofficePublisher;
import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.storefront.Catalog;
import com.rabbitlab.storefront.StorefrontConsumer;
import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M1 — Senaryo: Backoffice ürün oluşturur, Storefront kataloğuna yansır (US1).
 *
 * <p>Tüketim asenkron olduğu için sonucu Awaitility ile bekliyoruz: "en fazla 5 saniye içinde
 * bu koşul doğru olmalı". Thread.sleep kullanmıyoruz; çünkü ya gereğinden uzun bekler (yavaş test)
 * ya da kısa bekler (rastgele kırılan test).
 */
class ProductSyncFlowTest extends RabbitMqTestSupport {

    private final Catalog catalog = new Catalog();
    private String queue;

    @BeforeEach
    void createQueue() throws Exception {
        queue = uniqueName("storefront.catalog");
        try (Channel channel = connection.createChannel()) {
            declareQueue(channel, queue);
        }
    }

    @Test
    void should_show_product_in_catalog_when_backoffice_creates_it() throws Exception {
        try (StorefrontConsumer consumer = new StorefrontConsumer(connection, queue, catalog::apply);
             BackofficePublisher publisher = BackofficePublisher.directToQueue(connection, queue)) {
            consumer.start();

            publisher.publish(new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(catalog.find("SKU-1")).hasValueSatisfying(item -> {
                        assertThat(item.name()).isEqualTo("Kupa");
                        assertThat(item.active()).isTrue();
                        assertThat(item.outOfStock()).isTrue();
                    }));
        }
    }

    @Test
    void should_apply_create_before_update_when_single_consumer_reads_in_fifo_order() throws Exception {
        try (StorefrontConsumer consumer = new StorefrontConsumer(connection, queue, catalog::apply);
             BackofficePublisher publisher = BackofficePublisher.directToQueue(connection, queue)) {
            consumer.start();

            // Sıra bozulsaydı güncelleme "bilinmeyen ürün" hatası alırdı.
            publisher.publish(new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1));
            publisher.publish(new ProductUpdated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("120"), "TRY", 2));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(catalog.find("SKU-1")).hasValueSatisfying(item ->
                            assertThat(item.price()).isEqualByComparingTo("120")));
        }
    }
}
