package com.rabbitlab.m4;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Return;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M4 — Publisher tarafında güvenilirlik (öğrenme testleri).
 *
 * <p>Kavram: {@code basicPublish} "gönder ve unut"tur; döndüğünde mesajın broker'a ulaştığını
 * BİLMEYİZ. İki araç var:
 * <ul>
 *   <li>Publisher confirms: broker mesajı sahiplendiğinde (kalıcı mesajsa diske yazdığında) "ack" gönderir.</li>
 *   <li>Mandatory flag: mesaj hiçbir kuyruğa yönlendirilemezse broker onu sessizce silmek yerine
 *       publisher'a geri döndürür (basic.return).</li>
 * </ul>
 */
class PublisherConfirmTest extends RabbitMqTestSupport {

    @Test
    void should_receive_confirm_when_broker_accepts_message() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("confirm");
            declareQueue(channel, queue);
            channel.confirmSelect();

            channel.basicPublish("", queue, null, "stok:8".getBytes(UTF_8));

            assertThat(channel.waitForConfirms(5_000)).isTrue();
        }
    }

    @Test
    void should_return_message_to_publisher_when_mandatory_and_unroutable() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String exchange = uniqueName("lonely");
            channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
            AtomicReference<Return> returned = new AtomicReference<>();
            channel.addReturnListener(returned::set);

            channel.basicPublish(exchange, "stock.updated", true, null, "stok:8".getBytes(UTF_8));

            await().atMost(Duration.ofSeconds(3)).until(() -> returned.get() != null);
            assertThat(returned.get().getReplyCode()).isEqualTo(312); // NO_ROUTE
            assertThat(returned.get().getRoutingKey()).isEqualTo("stock.updated");
        }
    }
}
