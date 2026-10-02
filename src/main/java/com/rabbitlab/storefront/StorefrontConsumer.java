package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.messaging.MessageCodec;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Storefront tarafı: kuyruktan mesajları okur ve kataloğa uygular.
 *
 * <p>M1: autoAck=true. Broker mesajı gönderdiği anda "teslim edildi" sayar ve kuyruktan siler.
 * İşleme sırasında uygulama çökerse mesaj kaybolur. (M2'de bunu düzelteceğiz.)
 */
public final class StorefrontConsumer implements AutoCloseable {

    private final Channel channel;
    private final String queue;
    private final Catalog catalog;
    private final MessageCodec codec = new MessageCodec();

    public StorefrontConsumer(Connection connection, String queue, Catalog catalog) throws IOException {
        this.channel = connection.createChannel();
        this.queue = queue;
        this.catalog = catalog;
    }

    public void start() throws IOException {
        channel.basicConsume(queue, true, this::handle, consumerTag -> { });
    }

    private void handle(String consumerTag, Delivery delivery) {
        ProductEvent event = codec.decode(delivery.getProperties().getType(), delivery.getBody());
        catalog.apply(event);
    }

    @Override
    public void close() throws IOException, TimeoutException {
        if (channel.isOpen()) {
            channel.close();
        }
    }
}
