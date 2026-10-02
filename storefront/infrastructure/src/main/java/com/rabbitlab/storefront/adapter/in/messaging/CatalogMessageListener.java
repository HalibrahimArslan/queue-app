package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.storefront.application.port.in.CatalogUpdate;
import com.rabbitlab.storefront.application.port.in.UpdateCatalogUseCase;
import com.rabbitlab.storefront.domain.InvalidValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Storefront'un RabbitMQ girişi. Use case'i çağırır; RabbitMQ'ya özgü her şey (ack, x-death, park)
 * burada kalır. Faz 1'de bunların hepsi tek sınıftaydı ve iş mantığıyla iç içeydi (acıtan nokta 1).
 *
 * <table>
 *   <tr><th>Durum</th><th>Tepki</th></tr>
 *   <tr><td>Başarılı</td><td>ack</td></tr>
 *   <tr><td>Daha önce işlenmiş message-id</td><td>hiçbir şey yapmadan ack</td></tr>
 *   <tr><td>Bozuk/kurallara aykırı mesaj</td><td>park kuyruğuna taşı, ack (tekrar denemek işe yaramaz)</td></tr>
 *   <tr><td>Geçici hata (bilinmeyen ürün, DB kopuk...)</td>
 *       <td>reject → bekleme odası → gecikmeli tekrar; {@code max-attempts} sonra park</td></tr>
 * </table>
 * Faz 1'deki iki modlu consumer ({@code parkingQueue == null}) yok: tek politika var (acıtan nokta 5).
 */
@Component
class CatalogMessageListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogMessageListener.class);
    private static final long PARK_CONFIRM_TIMEOUT_MS = 5_000;

    private final MessageTranslator translator;
    private final UpdateCatalogUseCase updateCatalog;
    private final ProcessedMessages processed;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final MessagingProperties properties;

    CatalogMessageListener(MessageTranslator translator, UpdateCatalogUseCase updateCatalog,
                           ProcessedMessages processed, TransactionTemplate transaction, RabbitTemplate rabbit,
                           MessagingProperties properties) {
        this.translator = translator;
        this.updateCatalog = updateCatalog;
        this.processed = processed;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.properties = properties;
    }

    @RabbitListener(queues = StorefrontTopology.CATALOG_QUEUE)
    void onCatalogMessage(Message message) {
        handle(message, StorefrontTopology.CATALOG_QUEUE);
    }

    @RabbitListener(queues = StorefrontTopology.STOCK_QUEUE)
    void onStockMessage(Message message) {
        handle(message, StorefrontTopology.STOCK_QUEUE);
    }

    private void handle(Message message, String queue) {
        long attempt = rejectedCount(message.getMessageProperties(), queue) + 1;
        try {
            CatalogUpdate update = translator.translate(message);
            apply(message.getMessageProperties().getMessageId(), update);
        } catch (InvalidMessageException | InvalidValueException e) {
            park(message, queue, attempt, e);
        } catch (RuntimeException e) {
            if (attempt >= properties.maxAttempts()) {
                park(message, queue, attempt, e);
            } else {
                log.info("Geçici hata, {}. deneme başarısız; bekleme odasına: {}", attempt, e.getMessage());
                throw new AmqpRejectAndDontRequeueException(e); // DLX → <kuyruk>.retry
            }
        }
    }

    /**
     * Inbox kaydı ve katalog güncellemesi tek transaction: use case'in kendi transaction'ı bu
     * transaction'a katılır. Güncelleme başarısız olursa inbox kaydı da geri alınır.
     */
    private void apply(String messageId, CatalogUpdate update) {
        transaction.executeWithoutResult(status -> {
            if (messageId != null && !processed.markProcessed(messageId)) {
                log.debug("Tekrar gelen mesaj atlandı: {}", messageId);
                return;
            }
            updateCatalog.apply(update);
        });
    }

    /** Kaçıncı deneme? x-death'te bu kuyruktan "rejected" sebebiyle kaç kez çıktığına bakar. */
    static long rejectedCount(MessageProperties properties, String queue) {
        List<Map<String, ?>> deaths = properties.getXDeathHeader();
        if (deaths == null) {
            return 0;
        }
        return deaths.stream()
                .filter(death -> queue.equals(String.valueOf(death.get("queue")))
                        && "rejected".equals(String.valueOf(death.get("reason"))))
                .map(death -> death.get("count"))
                .filter(Number.class::isInstance)
                .mapToLong(count -> ((Number) count).longValue())
                .findFirst()
                .orElse(0);
    }

    /**
     * Mesajı hata bilgisiyle park kuyruğuna kopyalar ve onayını bekler. Listener normal döndüğünde
     * orijinal mesaj ack'lenir. Onay gelmezse exception fırlar, orijinal reject edilir; mesaj kaybolmaz.
     */
    private void park(Message message, String queue, long attempts, RuntimeException cause) {
        log.warn("Mesaj park ediliyor ({} deneme): {}", attempts, cause.getMessage());
        MessageProperties props = message.getMessageProperties();
        props.setHeader("x-error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        props.setHeader("x-original-queue", queue);
        props.setHeader("x-attempts", attempts);
        rabbit.invoke(operations -> {
            operations.send("", StorefrontTopology.parkingQueueOf(queue), message);
            operations.waitForConfirmsOrDie(PARK_CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
