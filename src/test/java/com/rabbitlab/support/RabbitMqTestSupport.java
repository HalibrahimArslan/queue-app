package com.rabbitlab.support;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

/**
 * Tüm entegrasyon testlerinin ortak tabanı.
 *
 * <p>Singleton container deseni: RabbitMQ container'ı JVM başına BİR kez açılır ve tüm test
 * sınıfları onu paylaşır. Testler birbirini etkilemesin diye her test kendi kuyruğunu
 * {@link #uniqueName(String)} ile rastgele isimle oluşturur. Container'ı test bitince
 * Testcontainers'ın "Ryuk" yardımcı container'ı kapatır.
 */
public abstract class RabbitMqTestSupport {

    protected static final String USERNAME = "rabbitlab";
    protected static final String PASSWORD = "rabbitlab";

    protected static final GenericContainer<?> RABBIT = newRabbitContainer();

    static {
        RABBIT.start();
    }

    /** Her test kendi TCP bağlantısını açar ve kapatır. */
    protected Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = connectionFactoryFor(RABBIT).newConnection("test");
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    /** Kendi broker'ına ihtiyaç duyan testler (ör. broker restart) için ayrı container üretir. */
    protected static GenericContainer<?> newRabbitContainer() {
        return new GenericContainer<>(DockerImageName.parse("rabbitmq:4.1-management"))
                .withExposedPorts(5672, 15672)
                .withEnv("RABBITMQ_DEFAULT_USER", USERNAME)
                .withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD)
                .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    protected static ConnectionFactory connectionFactoryFor(GenericContainer<?> container) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(container.getHost());
        factory.setPort(container.getMappedPort(5672));
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        // Testlerde sürpriz istemiyoruz: bağlantı koparsa kendiliğinden yeniden bağlanmasın.
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }

    protected static String uniqueName(String base) {
        return base + "." + UUID.randomUUID().toString().substring(0, 8);
    }
}
