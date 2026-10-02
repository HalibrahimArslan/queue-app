package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.messaging.InvalidMessageException;
import com.rabbitlab.messaging.MessageCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Storefront tarafı: kuyruktan mesajları okur ve handler'a (kataloğa) verir.
 *
 * <p>Mesaj ancak başarıyla işlendikten SONRA ack'lenir; çökersek broker mesajı başka bir
 * instance'a verir (at-least-once).
 *
 * <p>Hata durumunda iki mod var:
 * <table>
 *   <tr><th></th><th>Basit mod (M2, constructor)</th><th>Retry modu (M5, {@link #withRetry})</th></tr>
 *   <tr><td>Bozuk mesaj</td><td>reject → silinir</td><td>park kuyruğuna (DLQ) taşınır</td></tr>
 *   <tr><td>Geçici hata</td><td>nack+requeue → anında tekrar</td>
 *       <td>reject → bekleme odası → gecikmeli tekrar; {@code maxAttempts} sonra park</td></tr>
 * </table>
 */
public final class StorefrontConsumer implements AutoCloseable {

    public static final int DEFAULT_PREFETCH = 10;
    private static final long PARK_CONFIRM_TIMEOUT_MS = 5_000;

    private final Channel channel;
    private final String queue;
    private final ProductEventHandler handler;
    private final int prefetch;
    private final String parkingQueue;   // null → basit mod
    private final int maxAttempts;
    private final MessageCodec codec = new MessageCodec();
    private final AtomicInteger processed = new AtomicInteger();

    public StorefrontConsumer(Connection connection, String queue, ProductEventHandler handler) throws IOException {
        this(connection, queue, handler, DEFAULT_PREFETCH);
    }

    public StorefrontConsumer(Connection connection, String queue, ProductEventHandler handler, int prefetch)
            throws IOException {
        this(connection, queue, handler, prefetch, null, 1);
    }

    private StorefrontConsumer(Connection connection, String queue, ProductEventHandler handler, int prefetch,
                               String parkingQueue, int maxAttempts) throws IOException {
        this.channel = connection.createChannel();
        this.queue = queue;
        this.handler = handler;
        this.prefetch = prefetch;
        this.parkingQueue = parkingQueue;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Kuyruk, {@link com.rabbitlab.messaging.Topology} ile bekleme odası (DLX + TTL) ayarlanmış olmalı.
     */
    public static StorefrontConsumer withRetry(Connection connection, String queue, String parkingQueue,
                                               int maxAttempts, ProductEventHandler handler, int prefetch)
            throws IOException {
        StorefrontConsumer consumer =
                new StorefrontConsumer(connection, queue, handler, prefetch, parkingQueue, maxAttempts);
        consumer.channel.confirmSelect(); // park kuyruğuna taşınan mesaj kaybolmasın
        return consumer;
    }

    public void start() throws IOException {
        channel.basicQos(prefetch);
        channel.basicConsume(queue, false, this::handle, consumerTag -> { });
    }

    /** Bu instance'ın başarıyla işleyip ack'lediği mesaj sayısı. */
    public int processedCount() {
        return processed.get();
    }

    private void handle(String consumerTag, Delivery delivery) throws IOException {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        try {
            ProductEvent event = codec.decode(delivery.getProperties().getType(), delivery.getBody());
            handler.handle(event);
            channel.basicAck(deliveryTag, false);
            processed.incrementAndGet();
        } catch (InvalidMessageException e) {
            if (retryEnabled()) {
                park(delivery, e);
            } else {
                channel.basicReject(deliveryTag, false);
            }
        } catch (RuntimeException e) {
            if (!retryEnabled()) {
                channel.basicNack(deliveryTag, false, true);
            } else if (attemptOf(delivery) >= maxAttempts) {
                park(delivery, e);
            } else {
                channel.basicReject(deliveryTag, false); // DLX → bekleme odası
            }
        }
    }

    private boolean retryEnabled() {
        return parkingQueue != null;
    }

    /** Kaçıncı deneme? x-death'te bu kuyruktan "rejected" sebebiyle kaç kez çıktığına bakar. */
    private long attemptOf(Delivery delivery) {
        return rejectedCount(delivery.getProperties(), queue) + 1;
    }

    static long rejectedCount(AMQP.BasicProperties properties, String queue) {
        Map<String, Object> headers = properties.getHeaders();
        if (headers == null || !(headers.get("x-death") instanceof List<?> deaths)) {
            return 0;
        }
        for (Object entry : deaths) {
            if (entry instanceof Map<?, ?> death
                    && queue.equals(String.valueOf(death.get("queue")))
                    && "rejected".equals(String.valueOf(death.get("reason")))
                    && death.get("count") instanceof Number count) {
                return count.longValue();
            }
        }
        return 0;
    }

    /** Mesajı hata bilgisiyle park kuyruğuna kopyalar, onay gelince orijinali ack'ler. */
    private void park(Delivery delivery, RuntimeException cause) throws IOException {
        Map<String, Object> headers = new HashMap<>();
        if (delivery.getProperties().getHeaders() != null) {
            headers.putAll(delivery.getProperties().getHeaders());
        }
        headers.put("x-error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        headers.put("x-original-queue", queue);
        AMQP.BasicProperties properties = delivery.getProperties().builder().headers(headers).build();

        channel.basicPublish("", parkingQueue, properties, delivery.getBody());
        try {
            channel.waitForConfirmsOrDie(PARK_CONFIRM_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Park onayı beklenirken kesildi", e);
        } catch (TimeoutException e) {
            throw new IOException("Park kuyruğu onay vermedi", e);
        }
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    }

    @Override
    public void close() throws IOException, TimeoutException {
        if (channel.isOpen()) {
            channel.close();
        }
    }
}
