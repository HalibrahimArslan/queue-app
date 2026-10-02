package com.rabbitlab.m5;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M5 — Dead Letter Exchange ve TTL (öğrenme testleri).
 *
 * <p>Kavram: Bir kuyruğa {@code x-dead-letter-exchange} verirsen, o kuyruktan "ölen" mesajlar
 * silinmek yerine o exchange'e yeniden yayınlanır. Bir mesaj üç şekilde ölür:
 * <ul>
 *   <li>rejected — consumer reject/nack etti ve requeue=false dedi</li>
 *   <li>expired  — TTL süresi doldu</li>
 *   <li>maxlen   — kuyruk uzunluk sınırı aşıldı</li>
 * </ul>
 * Broker ölen mesaja {@code x-death} header'ı ekler: hangi kuyruktan, neden, kaç kez.
 * Burada DLX olarak default exchange'i ("") kullanıp {@code x-dead-letter-routing-key} ile
 * hedef kuyruğu doğrudan seçiyoruz.
 */
class DeadLetterTest extends RabbitMqTestSupport {

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
    void should_move_message_to_dead_letter_queue_when_rejected_without_requeue() throws Exception {
        String deadLetters = uniqueName("dlq");
        declareQueue(channel, deadLetters);
        String queue = uniqueName("work");
        channel.queueDeclare(queue, true, false, false, Map.of(
                "x-dead-letter-exchange", "",
                "x-dead-letter-routing-key", deadLetters));
        channel.basicPublish("", queue, null, "bozuk".getBytes(UTF_8));

        GetResponse received = channel.basicGet(queue, false);
        channel.basicReject(received.getEnvelope().getDeliveryTag(), false);

        GetResponse dead = awaitMessage(deadLetters);
        assertThat(new String(dead.getBody(), UTF_8)).isEqualTo("bozuk");
        assertThat(firstDeath(dead)).containsEntry("reason", "rejected").containsEntry("queue", queue);
    }

    @Test
    void should_move_message_to_dead_letter_queue_when_ttl_expires() throws Exception {
        String deadLetters = uniqueName("dlq");
        declareQueue(channel, deadLetters);
        String waitingRoom = uniqueName("waiting");
        channel.queueDeclare(waitingRoom, true, false, false, Map.of(
                "x-message-ttl", 200,
                "x-dead-letter-exchange", "",
                "x-dead-letter-routing-key", deadLetters));

        channel.basicPublish("", waitingRoom, null, "bekle".getBytes(UTF_8));

        // Kimse okumadı; 200 ms sonra mesaj kendiliğinden diğer kuyruğa geçti.
        GetResponse dead = awaitMessage(deadLetters);
        assertThat(firstDeath(dead)).containsEntry("reason", "expired");
    }

    private GetResponse awaitMessage(String queue) {
        AtomicReference<GetResponse> holder = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            holder.set(channel.basicGet(queue, true));
            return holder.get() != null;
        });
        return holder.get();
    }

    /** x-death değerleri LongString tipinde gelir; okunabilir olsun diye String'e çeviriyoruz. */
    private static Map<String, String> firstDeath(GetResponse response) {
        List<?> deaths = (List<?>) response.getProps().getHeaders().get("x-death");
        Map<?, ?> death = (Map<?, ?>) deaths.get(0);
        return Map.of(
                "reason", String.valueOf(death.get("reason")),
                "queue", String.valueOf(death.get("queue")));
    }
}
