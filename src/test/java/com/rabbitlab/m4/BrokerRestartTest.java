package com.rabbitlab.m4;

import com.rabbitlab.support.RabbitMqTestSupport;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 — Broker yeniden başlarsa ne olur? (öğrenme testi)
 *
 * <p>Kavram: Kalıcılık İKİ ayara bağlıdır:
 * <ul>
 *   <li>Kuyruk durable olmalı (kuyruğun TANIMI restart'tan sağ çıkar)</li>
 *   <li>Mesaj persistent olmalı, deliveryMode=2 (mesajın KENDİSİ diske yazılır)</li>
 * </ul>
 * Durable kuyruktaki persistent olmayan mesaj restart'ta kaybolur.
 *
 * <p>Bu test paylaşılan broker'ı bozmamak için kendi container'ını açar.
 */
class BrokerRestartTest extends RabbitMqTestSupport {

    private static GenericContainer<?> ownBroker;

    @BeforeAll
    static void startOwnBroker() {
        ownBroker = newRabbitContainer();
        ownBroker.start();
    }

    @AfterAll
    static void stopOwnBroker() {
        ownBroker.stop();
    }

    @Test
    void should_keep_only_persistent_messages_when_broker_restarts() throws Exception {
        ConnectionFactory factory = connectionFactoryFor(ownBroker);
        String persistentQueue = uniqueName("persistent");
        String transientQueue = uniqueName("transient");

        try (Connection before = factory.newConnection(); Channel channel = before.createChannel()) {
            declareQueue(channel, persistentQueue);
            declareQueue(channel, transientQueue);
            channel.confirmSelect();
            channel.basicPublish("", persistentQueue, MessageProperties.PERSISTENT_TEXT_PLAIN, "stok:8".getBytes(UTF_8));
            channel.basicPublish("", transientQueue, MessageProperties.TEXT_PLAIN, "stok:8".getBytes(UTF_8));
            channel.waitForConfirmsOrDie(5_000);
        }

        restartBroker();

        try (Connection after = factory.newConnection(); Channel channel = after.createChannel()) {
            assertThat(channel.queueDeclarePassive(persistentQueue).getMessageCount()).isEqualTo(1);
            assertThat(channel.queueDeclarePassive(transientQueue).getMessageCount()).isZero();
        }
    }

    /** Container'ı değil, içindeki RabbitMQ uygulamasını durdurup başlatır (port eşlemesi değişmesin diye). */
    private static void restartBroker() throws Exception {
        ExecResult stop = ownBroker.execInContainer("rabbitmqctl", "stop_app");
        assertThat(stop.getExitCode()).as(stop.getStderr()).isZero();
        ExecResult start = ownBroker.execInContainer("rabbitmqctl", "start_app");
        assertThat(start.getExitCode()).as(start.getStderr()).isZero();
        ExecResult ready = ownBroker.execInContainer("rabbitmqctl", "await_startup");
        assertThat(ready.getExitCode()).as(ready.getStderr()).isZero();
    }
}
