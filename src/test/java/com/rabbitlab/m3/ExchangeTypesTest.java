package com.rabbitlab.m3;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M3 — Exchange tipleri (öğrenme testleri).
 *
 * <p>Kavram: Publisher kuyrukları tanımaz; mesajı bir exchange'e bir routing key ile bırakır.
 * Hangi kuyruğun mesajı alacağına exchange, kuyrukların kendilerini bağladığı BINDING'lere
 * bakarak karar verir. Exchange tipi bu kararın kuralını belirler:
 * <ul>
 *   <li>direct  — routing key, binding key ile birebir aynı olmalı</li>
 *   <li>fanout  — routing key önemsiz, bağlı tüm kuyruklara kopya gider</li>
 *   <li>topic   — noktalı kelimeler + joker: {@code *} tam bir kelime, {@code #} sıfır veya daha fazla kelime</li>
 *   <li>headers — routing key yerine mesaj header'larına bakar</li>
 * </ul>
 */
class ExchangeTypesTest extends RabbitMqTestSupport {

    private Channel channel;

    @BeforeEach
    void openChannel() throws Exception {
        channel = connection.createChannel();
    }

    @AfterEach
    void closeChannel() throws Exception {
        channel.close();
    }

    @Test
    void should_route_only_to_exact_binding_key_when_exchange_is_direct() throws Exception {
        String exchange = uniqueName("direct");
        channel.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, true);
        String stockQueue = boundQueue(exchange, "stock.updated");
        String productQueue = boundQueue(exchange, "product.created");

        publish(exchange, "stock.updated");

        assertEventuallyCount(stockQueue, 1);
        assertThat(messageCount(productQueue)).isZero();
    }

    @Test
    void should_copy_to_every_bound_queue_when_exchange_is_fanout() throws Exception {
        String exchange = uniqueName("fanout");
        channel.exchangeDeclare(exchange, BuiltinExchangeType.FANOUT, true);
        String storefront = boundQueue(exchange, "");
        String searchIndex = boundQueue(exchange, "");

        publish(exchange, "herhangi.bir.key");

        assertEventuallyCount(storefront, 1);
        assertEventuallyCount(searchIndex, 1);
    }

    @Test
    void should_match_single_word_with_star_and_many_words_with_hash_when_exchange_is_topic() throws Exception {
        String exchange = uniqueName("topic");
        channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
        String star = boundQueue(exchange, "product.*");
        String hash = boundQueue(exchange, "product.#");

        publish(exchange, "product.created");
        publish(exchange, "product.price.changed");
        publish(exchange, "stock.updated");

        assertEventuallyCount(star, 1);  // sadece product.created
        assertEventuallyCount(hash, 2);  // product.created + product.price.changed
    }

    @Test
    void should_route_by_headers_when_exchange_is_headers() throws Exception {
        String exchange = uniqueName("headers");
        channel.exchangeDeclare(exchange, BuiltinExchangeType.HEADERS, true);
        String turkeyStock = uniqueName("q");
        declareQueue(channel, turkeyStock);
        channel.queueBind(turkeyStock, exchange, "", Map.of("x-match", "all", "event", "StockUpdated", "region", "TR"));

        publishWithHeaders(exchange, Map.of("event", "StockUpdated", "region", "TR"));
        publishWithHeaders(exchange, Map.of("event", "StockUpdated", "region", "DE"));

        assertEventuallyCount(turkeyStock, 1);
    }

    @Test
    void should_silently_drop_message_when_no_binding_matches() throws Exception {
        String exchange = uniqueName("nobody");
        channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
        String productQueue = boundQueue(exchange, "product.*");

        publish(exchange, "stock.updated"); // hata yok! mesaj sessizce kayboldu (M4: mandatory flag)

        assertThat(messageCount(productQueue)).isZero();
    }

    private String boundQueue(String exchange, String bindingKey) throws Exception {
        String queue = uniqueName("q");
        declareQueue(channel, queue);
        channel.queueBind(queue, exchange, bindingKey);
        return queue;
    }

    private void publish(String exchange, String routingKey) throws Exception {
        channel.basicPublish(exchange, routingKey, null, routingKey.getBytes(UTF_8));
    }

    private void publishWithHeaders(String exchange, Map<String, Object> headers) throws Exception {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(headers).build();
        channel.basicPublish(exchange, "", props, "x".getBytes(UTF_8));
    }

    /** Yönlendirme broker içinde asenkron; beklenen sayıya ulaşmasını kısa süre bekliyoruz. */
    private void assertEventuallyCount(String queue, int expected) {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(messageCount(queue)).isEqualTo(expected));
    }

    private int messageCount(String queue) throws Exception {
        return channel.queueDeclarePassive(queue).getMessageCount();
    }
}
