package com.rabbitlab.backoffice;

import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.messaging.MessageCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeoutException;

/**
 * Backoffice tarafı: event'leri RabbitMQ'ya gönderir.
 *
 * <p>M1: default exchange ("") üzerinden doğrudan tek bir kuyruğa gönderir.
 * Channel thread-safe olmadığı için {@code publish} senkronize.
 */
public final class BackofficePublisher implements AutoCloseable {

    private final Channel channel;
    private final String queue;
    private final MessageCodec codec = new MessageCodec();

    public BackofficePublisher(Connection connection, String queue) throws IOException {
        this.channel = connection.createChannel();
        this.queue = queue;
    }

    public synchronized void publish(ProductEvent event) {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .type(codec.typeOf(event))
                .build();
        try {
            channel.basicPublish("", queue, properties, codec.encode(event));
        } catch (IOException e) {
            throw new UncheckedIOException("Mesaj gönderilemedi: " + event, e);
        }
    }

    @Override
    public void close() throws IOException, TimeoutException {
        if (channel.isOpen()) {
            channel.close();
        }
    }
}
