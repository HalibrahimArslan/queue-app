package com.rabbitlab.m4;

import com.rabbitlab.backoffice.BackofficePublisher;
import com.rabbitlab.backoffice.PublishFailedException;
import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;
import com.rabbitlab.messaging.Topology;
import com.rabbitlab.storefront.Catalog;
import com.rabbitlab.storefront.StorefrontConsumer;
import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * M4 — Senaryo: Backoffice "gönderdim" dediğinde mesaj gerçekten broker'da ve diskte olmalı.
 */
class ReliablePublisherTest extends RabbitMqTestSupport {

    private static ProductCreated created() {
        return new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1);
    }

    @Test
    void should_publish_persistent_messages_with_id() throws Exception {
        Topology topology = Topology.withPrefix(uniqueName("t"));
        try (Channel channel = connection.createChannel()) {
            topology.declare(channel);

            try (BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
                publisher.publish(created());
            }

            GetResponse response = channel.basicGet(topology.catalogQueue(), true);
            assertThat(response.getProps().getDeliveryMode()).isEqualTo(2);
            assertThat(response.getProps().getMessageId()).isNotBlank();
            assertThat(response.getProps().getContentType()).isEqualTo("application/json");
        }
    }

    @Test
    void should_fail_loudly_when_no_queue_listens_for_the_event() throws Exception {
        String exchange = uniqueName("empty.exchange");
        try (Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
        }

        try (BackofficePublisher publisher = BackofficePublisher.toExchange(connection, exchange)) {
            assertThatThrownBy(() -> publisher.publish(new StockUpdated("SKU-1", 8, 1)))
                    .isInstanceOf(PublishFailedException.class)
                    .hasMessageContaining("stock.updated");
        }
    }

    @Test
    void should_process_all_waiting_messages_when_storefront_comes_back_online() throws Exception {
        // US5-1: Storefront kapalıyken Backoffice çalışmaya devam eder; mesajlar kuyrukta bekler.
        Topology topology = Topology.withPrefix(uniqueName("t"));
        try (Channel channel = connection.createChannel()) {
            topology.declare(channel);
        }
        try (BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
            publisher.publish(created());
            publisher.publish(new ProductUpdated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("120"), "TRY", 2));
            publisher.publish(new StockUpdated("SKU-1", 7, 1));
        }

        Catalog catalog = new Catalog();
        try (StorefrontConsumer catalogConsumer = new StorefrontConsumer(connection, topology.catalogQueue(), catalog::apply);
             StorefrontConsumer stockConsumer = new StorefrontConsumer(connection, topology.stockQueue(), catalog::apply)) {
            catalogConsumer.start();
            stockConsumer.start();

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(catalog.find("SKU-1")).hasValueSatisfying(item -> {
                        assertThat(item.price()).isEqualByComparingTo("120");
                        assertThat(item.stock()).isEqualTo(7);
                    }));
        }
    }
}
