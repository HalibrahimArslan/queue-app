package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.messaging.InvalidMessageException;
import com.rabbitlab.messaging.MessageCodec;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Storefront tarafı: kuyruktan mesajları okur ve handler'a (kataloğa) verir.
 *
 * <p>M2: manuel ack. Mesaj ancak başarıyla işlendikten SONRA ack'lenir; çökersek broker
 * mesajı başka bir instance'a verir (at-least-once).
 * <ul>
 *   <li>Başarılı → {@code basicAck}</li>
 *   <li>Bozuk mesaj → {@code basicReject(requeue=false)}: tekrar denemek işe yaramaz,
 *       requeue edersek kuyruğu sonsuza kadar tıkar. (M5'te DLQ'ya taşınacak.)</li>
 *   <li>Beklenmeyen hata → {@code basicNack(requeue=true)}: geçici olabilir, tekrar dene.</li>
 * </ul>
 */
public final class StorefrontConsumer implements AutoCloseable {

    public static final int DEFAULT_PREFETCH = 10;

    private final Channel channel;
    private final String queue;
    private final ProductEventHandler handler;
    private final int prefetch;
    private final MessageCodec codec = new MessageCodec();
    private final AtomicInteger processed = new AtomicInteger();

    public StorefrontConsumer(Connection connection, String queue, ProductEventHandler handler) throws IOException {
        this(connection, queue, handler, DEFAULT_PREFETCH);
    }

    public StorefrontConsumer(Connection connection, String queue, ProductEventHandler handler, int prefetch)
            throws IOException {
        this.channel = connection.createChannel();
        this.queue = queue;
        this.handler = handler;
        this.prefetch = prefetch;
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
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException e) {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @Override
    public void close() throws IOException, TimeoutException {
        if (channel.isOpen()) {
            channel.close();
        }
    }
}
