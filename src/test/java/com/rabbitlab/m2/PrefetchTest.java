package com.rabbitlab.m2;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M2 — Prefetch ve competing consumers (öğrenme testleri).
 *
 * <p>Kavram: Prefetch (basicQos), bir consumer'ın aynı anda kaç tane ack'lenmemiş mesaj
 * tutabileceğidir. Sınır dolunca broker o consumer'a yeni mesaj göndermez, diğerlerine verir.
 * Aynı kuyruğu dinleyen consumer'lar mesajları paylaşır (competing consumers): her mesaj
 * yalnızca BİR consumer'a gider. Yük böyle dağıtılır.
 */
class PrefetchTest extends RabbitMqTestSupport {

    @Test
    void should_stop_delivering_when_prefetch_limit_is_reached() throws Exception {
        String queue = uniqueName("prefetch");
        AtomicInteger received = new AtomicInteger();

        try (Channel channel = connection.createChannel()) {
            declareQueue(channel, queue);
            for (int i = 0; i < 10; i++) {
                channel.basicPublish("", queue, null, ("m" + i).getBytes(UTF_8));
            }

            channel.basicQos(2);
            // Bu consumer hiç ack göndermiyor: elinde en fazla 2 mesaj tutabilir.
            channel.basicConsume(queue, false, (tag, delivery) -> received.incrementAndGet(), tag -> { });

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                    .until(() -> received.get() == 2);

            try (Channel observer = connection.createChannel()) {
                // Kalan 8 mesaj "ready" durumda bekliyor; 2 mesaj "unacked".
                assertThat(observer.queueDeclarePassive(queue).getMessageCount()).isEqualTo(8);
            }
        }
    }

    @Test
    void should_share_messages_when_two_consumers_compete_on_same_queue() throws Exception {
        String queue = uniqueName("work");
        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger secondCount = new AtomicInteger();

        try (Channel first = connection.createChannel();
             Channel second = connection.createChannel();
             Channel publisher = connection.createChannel()) {
            declareQueue(publisher, queue);

            first.basicQos(1);
            second.basicQos(1);
            first.basicConsume(queue, false, (tag, delivery) -> {
                firstCount.incrementAndGet();
                first.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }, tag -> { });
            second.basicConsume(queue, false, (tag, delivery) -> {
                secondCount.incrementAndGet();
                second.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }, tag -> { });

            for (int i = 0; i < 20; i++) {
                publisher.basicPublish("", queue, null, ("m" + i).getBytes(UTF_8));
            }

            await().atMost(Duration.ofSeconds(5)).until(() -> firstCount.get() + secondCount.get() == 20);
            assertThat(firstCount.get()).isPositive();
            assertThat(secondCount.get()).isPositive();
        }
    }
}
