package com.rabbitlab.m0;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 — İskelet.
 *
 * <p>Kavram: Connection, uygulama ile broker arasındaki gerçek TCP bağlantısıdır ve pahalıdır.
 * Channel ise bu bağlantının içindeki hafif, sanal bir oturumdur. Bir connection içinde
 * yüzlerce channel açılabilir; her iş parçacığı (thread) kendi channel'ını kullanmalıdır.
 */
class RabbitMqConnectionTest extends RabbitMqTestSupport {

    @Test
    void should_open_connection_when_broker_is_running() {
        assertThat(connection.isOpen()).isTrue();
    }

    @Test
    void should_open_many_channels_over_single_connection() throws Exception {
        try (Channel first = connection.createChannel();
             Channel second = connection.createChannel()) {

            assertThat(first.isOpen()).isTrue();
            assertThat(second.isOpen()).isTrue();
            assertThat(first.getChannelNumber()).isNotEqualTo(second.getChannelNumber());
            assertThat(first.getConnection()).isSameAs(second.getConnection());
        }
    }
}
