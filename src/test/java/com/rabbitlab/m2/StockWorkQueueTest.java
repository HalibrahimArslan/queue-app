package com.rabbitlab.m2;

import com.rabbitlab.backoffice.BackofficePublisher;
import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.StockUpdated;
import com.rabbitlab.storefront.Catalog;
import com.rabbitlab.storefront.StorefrontConsumer;
import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M2 — Senaryo: Storefront'un iki instance'ı stok kuyruğunu paylaşır (US3).
 *
 * <p>İki instance aynı kataloğu (gerçekte aynı veritabanını) günceller. Competing consumers
 * mesaj SIRASINI bozar: v49, v50'den sonra işlenebilir. Versiyon kontrolü sayesinde sonuç
 * yine doğru çıkar. "Sıra garantisi mi, ölçeklenebilirlik mi?" — ikisini birden alamazsın.
 */
class StockWorkQueueTest extends RabbitMqTestSupport {

    private final Catalog catalog = new Catalog();
    private String queue;

    @BeforeEach
    void createQueueAndProduct() throws Exception {
        queue = uniqueName("storefront.stock");
        try (Channel channel = connection.createChannel()) {
            declareQueue(channel, queue);
        }
        catalog.apply(new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1));
    }

    @Test
    void should_end_with_latest_stock_when_two_instances_process_out_of_order() throws Exception {
        try (StorefrontConsumer instanceA = new StorefrontConsumer(connection, queue, catalog::apply, 1);
             StorefrontConsumer instanceB = new StorefrontConsumer(connection, queue, catalog::apply, 1);
             BackofficePublisher publisher = BackofficePublisher.directToQueue(connection, queue)) {
            instanceA.start();
            instanceB.start();

            for (int version = 1; version <= 50; version++) {
                publisher.publish(new StockUpdated("SKU-1", version, version));
            }

            await().atMost(Duration.ofSeconds(10))
                    .until(() -> instanceA.processedCount() + instanceB.processedCount() == 50);
            assertThat(instanceA.processedCount()).isPositive();
            assertThat(instanceB.processedCount()).isPositive();
            assertThat(catalog.find("SKU-1").orElseThrow().stock()).isEqualTo(50);
        }
    }

    @Test
    void should_apply_stock_on_healthy_instance_when_other_instance_crashes_before_ack() throws Exception {
        try (BackofficePublisher publisher = BackofficePublisher.directToQueue(connection, queue)) {
            publisher.publish(new StockUpdated("SKU-1", 8, 1));
        }

        Channel crashingInstance = connection.createChannel();
        assertThat(crashingInstance.basicGet(queue, false)).isNotNull();
        crashingInstance.close(); // ack yok → mesaj kuyruğa döner

        try (StorefrontConsumer healthyInstance = new StorefrontConsumer(connection, queue, catalog::apply, 1)) {
            healthyInstance.start();

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(catalog.find("SKU-1").orElseThrow().stock()).isEqualTo(8));
        }
    }

    @Test
    void should_retry_message_when_processing_fails_unexpectedly() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        try (StorefrontConsumer consumer = new StorefrontConsumer(connection, queue, event -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("veritabanı geçici olarak erişilemez");
            }
            catalog.apply(event);
        }, 1);
             BackofficePublisher publisher = BackofficePublisher.directToQueue(connection, queue)) {
            consumer.start();

            publisher.publish(new StockUpdated("SKU-1", 8, 1));

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(catalog.find("SKU-1").orElseThrow().stock()).isEqualTo(8));
            assertThat(attempts.get()).isEqualTo(2);
        }
    }

    @Test
    void should_not_loop_forever_when_message_is_invalid() throws Exception {
        try (StorefrontConsumer consumer = new StorefrontConsumer(connection, queue, catalog::apply, 1);
             Channel channel = connection.createChannel()) {
            consumer.start();

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().type("StockUpdated").build();
            channel.basicPublish("", queue, props, "{\"sku\":\"SKU-1\",\"quantity\":-5,\"version\":1}".getBytes(UTF_8));
            channel.basicPublish("", queue, props, "{\"sku\":\"SKU-1\",\"quantity\":3,\"version\":2}".getBytes(UTF_8));

            // Bozuk mesaj kuyruğu tıkamadı: arkasındaki geçerli mesaj işlendi.
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(catalog.find("SKU-1").orElseThrow().stock()).isEqualTo(3));
            assertThat(channel.queueDeclarePassive(queue).getMessageCount()).isZero();
        }
    }
}
