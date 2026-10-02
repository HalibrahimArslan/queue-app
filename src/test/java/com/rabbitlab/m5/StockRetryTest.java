package com.rabbitlab.m5;

import com.rabbitlab.backoffice.BackofficePublisher;
import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.StockUpdated;
import com.rabbitlab.messaging.Topology;
import com.rabbitlab.storefront.Catalog;
import com.rabbitlab.storefront.StorefrontConsumer;
import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M5 — Senaryo: hatalı mesajlar sistemi kilitlemez (US5-2, US5-3).
 *
 * <pre>
 *  storefront.stock ──reject──▶ storefront.stock.retry (TTL) ──süre dolunca──▶ storefront.stock
 *        │
 *        └── 3. denemede de başarısız / bozuk mesaj ──▶ storefront.stock.dlq (park)
 * </pre>
 * Requeue yerine "bekleme odası" kullanıyoruz: requeue mesajı ANINDA geri koyar ve consumer'ı
 * aynı hatayla sonsuz bir döngüye sokar. Gecikmeli retry ise karşı tarafa toparlanma süresi verir.
 */
class StockRetryTest extends RabbitMqTestSupport {

    private static final int MAX_ATTEMPTS = 3;

    private final Catalog catalog = new Catalog();
    private Topology topology;
    private Channel channel;

    @BeforeEach
    void declareTopology() throws Exception {
        topology = Topology.withPrefix(uniqueName("t"), Duration.ofMillis(500));
        channel = connection.createChannel();
        topology.declare(channel);
    }

    @AfterEach
    void closeChannel() throws Exception {
        channel.close();
    }

    @Test
    void should_park_message_after_three_attempts_when_product_stays_unknown() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        try (StorefrontConsumer consumer = StorefrontConsumer.withRetry(connection, topology.stockQueue(),
                topology.deadLetterQueueOf(topology.stockQueue()), MAX_ATTEMPTS, event -> {
                    attempts.incrementAndGet();
                    catalog.apply(event);
                }, 1);
             BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
            consumer.start();

            publisher.publish(new StockUpdated("SKU-9", 4, 1));

            GetResponse parked = awaitMessage(topology.deadLetterQueueOf(topology.stockQueue()));
            assertThat(attempts.get()).isEqualTo(MAX_ATTEMPTS);
            assertThat(parked.getProps().getType()).isEqualTo("StockUpdated");
            assertThat(String.valueOf(parked.getProps().getHeaders().get("x-error"))).contains("SKU-9");
        }
    }

    @Test
    void should_apply_stock_when_product_arrives_while_stock_message_is_waiting_for_retry() throws Exception {
        AtomicInteger stockAttempts = new AtomicInteger();

        try (StorefrontConsumer catalogConsumer = StorefrontConsumer.withRetry(connection, topology.catalogQueue(),
                topology.deadLetterQueueOf(topology.catalogQueue()), MAX_ATTEMPTS, catalog::apply, 1);
             StorefrontConsumer stockConsumer = StorefrontConsumer.withRetry(connection, topology.stockQueue(),
                     topology.deadLetterQueueOf(topology.stockQueue()), MAX_ATTEMPTS, event -> {
                         stockAttempts.incrementAndGet();
                         catalog.apply(event);
                     }, 1);
             BackofficePublisher publisher = BackofficePublisher.toExchange(connection, topology.exchange())) {
            catalogConsumer.start();
            stockConsumer.start();

            publisher.publish(new StockUpdated("SKU-1", 6, 1));
            await().atMost(Duration.ofSeconds(3)).until(() -> stockAttempts.get() >= 1); // ilk deneme başarısız
            publisher.publish(new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(catalog.find("SKU-1")).hasValueSatisfying(item -> assertThat(item.stock()).isEqualTo(6)));
            assertThat(channel.queueDeclarePassive(topology.deadLetterQueueOf(topology.stockQueue())).getMessageCount())
                    .isZero();
        }
    }

    @Test
    void should_park_invalid_message_immediately_and_keep_processing_others() throws Exception {
        catalog.apply(new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100"), "TRY", 1));
        AtomicInteger handled = new AtomicInteger();

        try (StorefrontConsumer consumer = StorefrontConsumer.withRetry(connection, topology.stockQueue(),
                topology.deadLetterQueueOf(topology.stockQueue()), MAX_ATTEMPTS, event -> {
                    handled.incrementAndGet();
                    catalog.apply(event);
                }, 1)) {
            consumer.start();

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().type("StockUpdated").build();
            channel.basicPublish(topology.exchange(), "stock.updated", props,
                    "{\"sku\":\"SKU-1\",\"quantity\":-5,\"version\":1}".getBytes(UTF_8));
            channel.basicPublish(topology.exchange(), "stock.updated", props,
                    "{\"sku\":\"SKU-1\",\"quantity\":3,\"version\":2}".getBytes(UTF_8));

            GetResponse parked = awaitMessage(topology.deadLetterQueueOf(topology.stockQueue()));
            assertThat(new String(parked.getBody(), UTF_8)).contains("-5");
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(catalog.find("SKU-1").orElseThrow().stock()).isEqualTo(3));
            assertThat(handled.get()).isEqualTo(1); // bozuk mesaj handler'a hiç ulaşmadı, tekrar da denenmedi
        }
    }

    private GetResponse awaitMessage(String queue) {
        GetResponse[] holder = new GetResponse[1];
        await().atMost(Duration.ofSeconds(5)).until(() -> (holder[0] = channel.basicGet(queue, true)) != null);
        return holder[0];
    }
}
