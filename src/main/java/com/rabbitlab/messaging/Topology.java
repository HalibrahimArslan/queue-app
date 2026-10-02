package com.rabbitlab.messaging;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

import java.io.IOException;

/**
 * RabbitMQ'daki "altyapı haritası": hangi exchange, hangi kuyruklar, hangi binding'ler.
 *
 * <p>Declare işlemleri idempotenttir: aynı ayarlarla tekrar declare etmek hiçbir şeyi değiştirmez.
 * Bu yüzden hem publisher hem consumer uygulaması açılırken {@link #declare(Channel)} çağırabilir.
 */
public record Topology(String exchange, String catalogQueue, String stockQueue) {

    public static final String PRODUCT_EVENTS = "product.*";
    public static final String STOCK_EVENTS = "stock.*";

    public static Topology defaults() {
        return new Topology("backoffice.events", "storefront.catalog", "storefront.stock");
    }

    /** Testlerde her testin kendi izole topolojisi olsun diye. */
    public static Topology withPrefix(String prefix) {
        return new Topology(prefix + ".backoffice.events", prefix + ".storefront.catalog", prefix + ".storefront.stock");
    }

    public void declare(Channel channel) throws IOException {
        channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
        declareBoundQueue(channel, catalogQueue, PRODUCT_EVENTS);
        declareBoundQueue(channel, stockQueue, STOCK_EVENTS);
    }

    private void declareBoundQueue(Channel channel, String queue, String bindingKey) throws IOException {
        channel.queueDeclare(queue, true, false, false, null);
        channel.queueBind(queue, exchange, bindingKey);
    }
}
