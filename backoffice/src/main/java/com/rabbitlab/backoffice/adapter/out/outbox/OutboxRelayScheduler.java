package com.rabbitlab.backoffice.adapter.out.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Relay'i arka planda {@code poll-interval} aralıkla döndürür. Bir tur dolu geldiyse (batch-size kadar)
 * beklemeden devam eder; birikmiş outbox hızla boşalır.
 *
 * <p>{@code SmartLifecycle}: uygulama ayağa kalkınca başlar, kapanırken durur.
 */
@Component
@ConditionalOnProperty(name = "backoffice.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;
    private final OutboxProperties properties;
    private ScheduledExecutorService executor;

    OutboxRelayScheduler(OutboxRelay relay, OutboxProperties properties) {
        this.relay = relay;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").factory());
        executor.scheduleWithFixedDelay(this::tick, 0, properties.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            while (relay.relayPending() == properties.batchSize()) {
                // dolu tur: arkada daha var
            }
        } catch (RuntimeException e) {
            // Exception zamanlayıcıyı öldürmesin; DB veya broker geri gelince devam ederiz.
            log.warn("Outbox turu başarısız, sonraki turda tekrar denenecek", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(properties.confirmTimeout().toMillis() * 2, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
