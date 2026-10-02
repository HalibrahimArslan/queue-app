package com.rabbitlab.m3;

import com.rabbitlab.backoffice.BackofficePublisher;
import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductDeactivated;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;
import com.rabbitlab.messaging.Topology;
import com.rabbitlab.storefront.Catalog;
import com.rabbitlab.storefront.StorefrontConsumer;
import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M3 — Senaryo: Backoffice event'leri topic exchange'e gider, Storefront kuyrukları
 * ihtiyaç duydukları event'lere abone olur.
 *
 * <pre>
 *                        ┌── product.* ──▶ storefront.catalog
 *  Backoffice ──▶ backoffice.events (topic)
 *                        └── stock.*   ──▶ storefront.stock
 * </pre>
 */
class BackofficeTopologyTest extends RabbitMqTestSupport {

    private Topology topology;
    private Channel channel;

    @BeforeEach
    void declareTopology() throws Exception {
        topology = Topology.withPrefix(uniqueName("t"));
        channel = connection.createChannel();
        topology.declare(channel);
    }

    @AfterEach
    void closeChannel() throws Exception {
        channel.close();
    }

    private static ProductCreated created() {
        return new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1);
    }

    @Test
    void should_route_product_events_to_catalog_queue_and_stock_events_to_stock_queue() throws Exception {
        try (BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
            publisher.publish(created());
            publisher.publish(new ProductUpdated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("120"), "TRY", 2));
            publisher.publish(new StockUpdated("SKU-1", 5, 1));
            publisher.publish(new ProductDeactivated("SKU-1", 3));
        }

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(channel.queueDeclarePassive(topology.catalogQueue()).getMessageCount()).isEqualTo(3);
            assertThat(channel.queueDeclarePassive(topology.stockQueue()).getMessageCount()).isEqualTo(1);
        });
        assertThat(drainTypes(topology.catalogQueue()))
                .containsExactly("ProductCreated", "ProductUpdated", "ProductDeactivated");
        assertThat(drainTypes(topology.stockQueue())).containsExactly("StockUpdated");
    }

    @Test
    void should_deliver_to_new_listener_without_changing_publisher() throws Exception {
        // Yarın "arama indeksi" ekibi tüm event'leri istiyor. Backoffice'e tek satır dokunmuyoruz:
        // sadece yeni bir kuyruk + binding.
        String searchIndex = uniqueName("search.index");
        declareQueue(channel, searchIndex);
        channel.queueBind(searchIndex, topology.exchange(), "#");

        try (BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
            publisher.publish(created());
            publisher.publish(new StockUpdated("SKU-1", 5, 1));
        }

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(channel.queueDeclarePassive(searchIndex).getMessageCount()).isEqualTo(2));
    }

    @Test
    void should_sync_catalog_end_to_end_when_both_storefront_queues_are_consumed() throws Exception {
        Catalog catalog = new Catalog();

        try (StorefrontConsumer catalogConsumer = new StorefrontConsumer(connection, topology.catalogQueue(), catalog::apply);
             StorefrontConsumer stockConsumer = new StorefrontConsumer(connection, topology.stockQueue(), catalog::apply);
             BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
            catalogConsumer.start();
            stockConsumer.start();

            // DİKKAT: İki ayrı kuyruk arasında sıra garantisi YOK. Stok mesajı üründen önce gelebilir;
            // o zaman "bilinmeyen ürün" hatası alır, nack+requeue ile tekrar denenir. (M5'te daha iyisi.)
            publisher.publish(created());
            publisher.publish(new StockUpdated("SKU-1", 5, 1));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(catalog.find("SKU-1")).hasValueSatisfying(item -> {
                        assertThat(item.stock()).isEqualTo(5);
                        assertThat(item.outOfStock()).isFalse();
                    }));
        }
    }

    private List<String> drainTypes(String queue) throws Exception {
        List<String> types = new ArrayList<>();
        GetResponse response;
        while ((response = channel.basicGet(queue, true)) != null) {
            types.add(response.getProps().getType());
        }
        return types;
    }
}
