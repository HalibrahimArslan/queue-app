package com.rabbitlab.backoffice;

import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.messaging.EventRouting;
import com.rabbitlab.messaging.MessageCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Backoffice tarafı: event'leri RabbitMQ'ya gönderir.
 *
 * <ul>
 *   <li>{@link #toExchange}: asıl kullanım (M3). Event, tipine göre routing key ile topic exchange'e gider;
 *       publisher hangi kuyrukların dinlediğini bilmez.</li>
 *   <li>{@link #directToQueue}: M1/M2'deki basit kullanım; default exchange ile tek kuyruğa.</li>
 * </ul>
 * Channel thread-safe olmadığı için {@code publish} senkronize.
 */
public final class BackofficePublisher implements AutoCloseable {

    private final Channel channel;
    private final String exchange;
    private final Function<ProductEvent, String> routing;
    private final MessageCodec codec = new MessageCodec();

    private BackofficePublisher(Connection connection, String exchange, Function<ProductEvent, String> routing)
            throws IOException {
        this.channel = connection.createChannel();
        this.exchange = exchange;
        this.routing = routing;
    }

    public static BackofficePublisher toExchange(Connection connection, String exchange) throws IOException {
        return new BackofficePublisher(connection, exchange, EventRouting::routingKeyOf);
    }

    public static BackofficePublisher directToQueue(Connection connection, String queue) throws IOException {
        return new BackofficePublisher(connection, "", event -> queue);
    }

    public synchronized void publish(ProductEvent event) {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .type(codec.typeOf(event))
                .build();
        try {
            channel.basicPublish(exchange, routing.apply(event), properties, codec.encode(event));
        } catch (IOException e) {
            throw new UncheckedIOException("Mesaj gönderilemedi: " + event, e);
        }
    }

    @Override
    public void close() throws Exception {
        if (channel.isOpen()) {
            channel.close();
        }
    }
}
