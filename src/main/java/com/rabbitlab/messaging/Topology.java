package com.rabbitlab.messaging;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * RabbitMQ'daki "altyapı haritası": hangi exchange, hangi kuyruklar, hangi binding'ler.
 *
 * <pre>
 *                                    ┌─ product.* ─▶ storefront.catalog ⇄ storefront.catalog.retry
 *  Backoffice ─▶ backoffice.events ──┤                     └─▶ storefront.catalog.dlq
 *                (topic)             └─ stock.*   ─▶ storefront.stock   ⇄ storefront.stock.retry
 *                                                          └─▶ storefront.stock.dlq
 * </pre>
 *
 * <p>Her tüketici kuyruğu için (M5):
 * <ul>
 *   <li>{@code <kuyruk>}: reject edilen mesaj, DLX ile {@code <kuyruk>.retry}'a gider.</li>
 *   <li>{@code <kuyruk>.retry}: "bekleme odası". Kimse okumaz; TTL dolunca mesaj ana kuyruğa döner.</li>
 *   <li>{@code <kuyruk>.dlq}: "park kuyruğu". Artık denenmeyecek mesajlar; bir insan bakmalı.</li>
 * </ul>
 * DLX olarak default exchange ("") kullanılır; hedef kuyruk {@code x-dead-letter-routing-key} ile seçilir.
 *
 * <p>DİKKAT: Declare idempotenttir ama ayarlar (argümanlar) değişirse broker reddeder
 * (PRECONDITION_FAILED). Var olan bir kuyruğun ayarı değişecekse kuyruk silinip yeniden kurulmalı.
 */
public record Topology(String exchange, String catalogQueue, String stockQueue, Duration retryDelay) {

    public static final String PRODUCT_EVENTS = "product.*";
    public static final String STOCK_EVENTS = "stock.*";

    public static Topology defaults() {
        return new Topology("backoffice.events", "storefront.catalog", "storefront.stock", Duration.ofSeconds(5));
    }

    /** Testlerde her testin kendi izole topolojisi olsun diye. Retry beklemesi kısa tutulur. */
    public static Topology withPrefix(String prefix) {
        return withPrefix(prefix, Duration.ofMillis(200));
    }

    public static Topology withPrefix(String prefix, Duration retryDelay) {
        return new Topology(prefix + ".backoffice.events", prefix + ".storefront.catalog",
                prefix + ".storefront.stock", retryDelay);
    }

    public String retryQueueOf(String queue) {
        return queue + ".retry";
    }

    public String deadLetterQueueOf(String queue) {
        return queue + ".dlq";
    }

    public void declare(Channel channel) throws IOException {
        channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
        declareConsumerQueue(channel, catalogQueue, PRODUCT_EVENTS);
        declareConsumerQueue(channel, stockQueue, STOCK_EVENTS);
    }

    private void declareConsumerQueue(Channel channel, String queue, String bindingKey) throws IOException {
        channel.queueDeclare(queue, true, false, false, Map.of(
                "x-dead-letter-exchange", "",
                "x-dead-letter-routing-key", retryQueueOf(queue)));
        channel.queueBind(queue, exchange, bindingKey);

        channel.queueDeclare(retryQueueOf(queue), true, false, false, Map.of(
                "x-message-ttl", retryDelay.toMillis(),
                "x-dead-letter-exchange", "",
                "x-dead-letter-routing-key", queue));

        channel.queueDeclare(deadLetterQueueOf(queue), true, false, false, null);
    }
}
