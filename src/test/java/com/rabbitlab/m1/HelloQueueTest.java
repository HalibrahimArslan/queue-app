package com.rabbitlab.m1;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M1 — Hello Queue (öğrenme testleri, saf amqp-client).
 *
 * <p>Kavram: Producer mesajı hiçbir zaman doğrudan kuyruğa koymaz, her zaman bir EXCHANGE'e
 * gönderir. Adı boş string ("") olan "default exchange" özeldir: routing key olarak verilen
 * isimdeki kuyruğa mesajı iletir. Bu yüzden "kuyruğa gönderdim" sanırız.
 */
class HelloQueueTest extends RabbitMqTestSupport {

    @Test
    void should_deliver_message_to_queue_when_published_through_default_exchange() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("hello");
            declareQueue(channel, queue);

            channel.basicPublish("", queue, null, "merhaba".getBytes(UTF_8));

            GetResponse response = channel.basicGet(queue, true);
            assertThat(response).isNotNull();
            assertThat(new String(response.getBody(), UTF_8)).isEqualTo("merhaba");
        }
    }

    @Test
    void should_return_nothing_when_queue_is_empty() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("empty");
            declareQueue(channel, queue);

            assertThat(channel.basicGet(queue, true)).isNull();
        }
    }

    @Test
    void should_keep_fifo_order_when_single_consumer_reads() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("fifo");
            declareQueue(channel, queue);

            for (int i = 1; i <= 5; i++) {
                channel.basicPublish("", queue, null, ("mesaj-" + i).getBytes(UTF_8));
            }

            List<String> received = new ArrayList<>();
            GetResponse response;
            while ((response = channel.basicGet(queue, true)) != null) {
                received.add(new String(response.getBody(), UTF_8));
            }
            assertThat(received).containsExactly("mesaj-1", "mesaj-2", "mesaj-3", "mesaj-4", "mesaj-5");
        }
    }

    @Test
    void should_report_message_count_when_queue_is_declared_passively() throws Exception {
        try (Channel channel = connection.createChannel()) {
            String queue = uniqueName("count");
            declareQueue(channel, queue);
            channel.basicPublish("", queue, null, "a".getBytes(UTF_8));
            channel.basicPublish("", queue, null, "b".getBytes(UTF_8));

            // queueDeclarePassive kuyruğu oluşturmaz; sadece var mı diye bakar ve sayıları döner.
            assertThat(channel.queueDeclarePassive(queue).getMessageCount()).isEqualTo(2);
        }
    }
}
