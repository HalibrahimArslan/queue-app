package com.rabbitlab.m2;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M2 — Ack mekanizması (öğrenme testleri).
 *
 * <p>Kavram: Consumer mesajı aldıktan sonra broker'a "işledim, silebilirsin" (ack) der.
 * Ack gelmeden channel/bağlantı koparsa broker mesajı kuyruğa geri koyar ve başka bir
 * consumer'a "redelivered" işaretiyle tekrar verir. Bu, AT-LEAST-ONCE (en az bir kez) teslimdir:
 * mesaj kaybolmaz ama iki kez işlenebilir. Bu yüzden Storefront idempotent olmak zorunda.
 */
class AcknowledgementTest extends RabbitMqTestSupport {

    @Test
    void should_redeliver_message_when_consumer_dies_before_ack() throws Exception {
        String queue = uniqueName("ack");
        try (Channel publisher = connection.createChannel()) {
            declareQueue(publisher, queue);
            publisher.basicPublish("", queue, null, "stok:8".getBytes(UTF_8));
        }

        Channel crashingConsumer = connection.createChannel();
        GetResponse first = crashingConsumer.basicGet(queue, false);
        assertThat(first.getEnvelope().isRedeliver()).isFalse();
        crashingConsumer.close(); // ack göndermeden "çöktü"

        try (Channel healthyConsumer = connection.createChannel()) {
            // Kuyruğa geri koyma işlemi broker'da asenkron olabilir; kısa süre bekliyoruz.
            AtomicReference<GetResponse> redelivered = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> redelivered.updateAndGet(r -> r != null ? r : basicGetQuietly(healthyConsumer, queue)) != null);
            GetResponse second = redelivered.get();
            assertThat(new String(second.getBody(), UTF_8)).isEqualTo("stok:8");
            assertThat(second.getEnvelope().isRedeliver()).isTrue();
            healthyConsumer.basicAck(second.getEnvelope().getDeliveryTag(), false);
        }
    }

    @Test
    void should_lose_message_when_auto_ack_consumer_dies() throws Exception {
        String queue = uniqueName("autoack");
        try (Channel publisher = connection.createChannel()) {
            declareQueue(publisher, queue);
            publisher.basicPublish("", queue, null, "stok:8".getBytes(UTF_8));
        }

        Channel crashingConsumer = connection.createChannel();
        crashingConsumer.basicGet(queue, true); // autoAck: alındığı anda silindi
        crashingConsumer.close();

        try (Channel channel = connection.createChannel()) {
            assertThat(channel.queueDeclarePassive(queue).getMessageCount()).isZero();
        }
    }

    @Test
    void should_put_message_back_when_nacked_with_requeue() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("nack");
            declareQueue(channel, queue);
            channel.basicPublish("", queue, null, "stok:8".getBytes(UTF_8));

            GetResponse first = channel.basicGet(queue, false);
            channel.basicNack(first.getEnvelope().getDeliveryTag(), false, true);

            GetResponse again = channel.basicGet(queue, false);
            assertThat(again).isNotNull();
            assertThat(again.getEnvelope().isRedeliver()).isTrue();
        }
    }

    @Test
    void should_drop_message_when_rejected_without_requeue() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("reject");
            declareQueue(channel, queue);
            channel.basicPublish("", queue, null, "bozuk".getBytes(UTF_8));

            GetResponse first = channel.basicGet(queue, false);
            // requeue=false ve kuyrukta dead-letter ayarı yok: mesaj yok olur (M5'te DLQ ekleyeceğiz).
            channel.basicReject(first.getEnvelope().getDeliveryTag(), false);

            assertThat(channel.basicGet(queue, false)).isNull();
        }
    }

    private static GetResponse basicGetQuietly(Channel channel, String queue) {
        try {
            return channel.basicGet(queue, false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
