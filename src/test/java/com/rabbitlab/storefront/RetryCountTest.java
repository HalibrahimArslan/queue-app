package com.rabbitlab.storefront;

import com.rabbitmq.client.AMQP;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** x-death header'ından deneme sayısını okuma — broker'sız unit test. */
class RetryCountTest {

    private static AMQP.BasicProperties withDeaths(List<Map<String, Object>> deaths) {
        return new AMQP.BasicProperties.Builder().headers(Map.of("x-death", deaths)).build();
    }

    @Test
    void should_be_zero_when_message_never_died() {
        assertThat(StorefrontConsumer.rejectedCount(new AMQP.BasicProperties.Builder().build(), "stock")).isZero();
    }

    @Test
    void should_count_only_rejections_from_given_queue() {
        AMQP.BasicProperties properties = withDeaths(List.of(
                Map.of("queue", "stock.retry", "reason", "expired", "count", 2L),
                Map.of("queue", "stock", "reason", "rejected", "count", 2L),
                Map.of("queue", "catalog", "reason", "rejected", "count", 5L)));

        assertThat(StorefrontConsumer.rejectedCount(properties, "stock")).isEqualTo(2);
    }
}
