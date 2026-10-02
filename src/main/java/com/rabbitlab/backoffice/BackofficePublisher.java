package com.rabbitlab.backoffice;

import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.messaging.EventRouting;
import com.rabbitlab.messaging.MessageCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Return;

import java.io.IOException;
import java.time.Duration;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Backoffice tarafı: event'leri RabbitMQ'ya gönderir.
 *
 * <ul>
 *   <li>{@link #toExchange}: asıl kullanım (M3). Event, tipine göre routing key ile topic exchange'e gider;
 *       publisher hangi kuyrukların dinlediğini bilmez.</li>
 *   <li>{@link #directToQueue}: M1/M2'deki basit kullanım; default exchange ile tek kuyruğa.</li>
 * </ul>
 *
 * <p>M4 — güvenilirlik:
 * <ul>
 *   <li>Persistent mesaj (deliveryMode=2): broker restart'ta kaybolmaz (kuyruk da durable olmalı).</li>
 *   <li>Publisher confirms: {@code publish} ancak broker "aldım" dedikten sonra döner.</li>
 *   <li>Mandatory: hiçbir kuyruğa gitmeyen mesaj sessizce kaybolmaz, {@link PublishFailedException} fırlar.</li>
 * </ul>
 * Not: Her mesajda confirm beklemek basit ama yavaştır (saniyede yüzlerce mesaj). Yüksek hacimde
 * mesajlar toplu (batch) veya asenkron confirm ile gönderilir. Bizim senaryomuz için yeterli.
 *
 * <p>Channel thread-safe olmadığı için {@code publish} senkronize.
 */
public final class BackofficePublisher implements AutoCloseable {

    private static final int PERSISTENT = 2;
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final Channel channel;
    private final String exchange;
    private final Function<ProductEvent, String> routing;
    private final MessageCodec codec = new MessageCodec();
    private final Queue<Return> returned = new ConcurrentLinkedQueue<>();

    private BackofficePublisher(Connection connection, String exchange, Function<ProductEvent, String> routing)
            throws IOException {
        this.channel = connection.createChannel();
        this.exchange = exchange;
        this.routing = routing;
        channel.confirmSelect();
        // Broker, yönlendirilemeyen mandatory mesajı ack'ten ÖNCE geri gönderir.
        channel.addReturnListener(returned::add);
    }

    public static BackofficePublisher toExchange(Connection connection, String exchange) throws IOException {
        return new BackofficePublisher(connection, exchange, EventRouting::routingKeyOf);
    }

    public static BackofficePublisher directToQueue(Connection connection, String queue) throws IOException {
        return new BackofficePublisher(connection, "", event -> queue);
    }

    public synchronized void publish(ProductEvent event) {
        String routingKey = routing.apply(event);
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .type(codec.typeOf(event))
                .messageId(UUID.randomUUID().toString())
                .deliveryMode(PERSISTENT)
                .build();
        try {
            channel.basicPublish(exchange, routingKey, true, properties, codec.encode(event));
            channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
        } catch (IOException | TimeoutException e) {
            throw new PublishFailedException("Broker mesajı onaylamadı: " + event, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Onay beklerken kesildi: " + event, e);
        }

        Return unroutable = returned.poll();
        if (unroutable != null) {
            throw new PublishFailedException("Mesaj hiçbir kuyruğa yönlendirilemedi: exchange='%s' routingKey='%s' (%s)"
                    .formatted(unroutable.getExchange(), unroutable.getRoutingKey(), unroutable.getReplyText()));
        }
    }

    @Override
    public void close() throws Exception {
        if (channel.isOpen()) {
            channel.close();
        }
    }
}
