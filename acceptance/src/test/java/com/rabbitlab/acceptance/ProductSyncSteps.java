package com.rabbitlab.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.tr.Diyelimki;
import io.cucumber.java.tr.Eğerki;
import io.cucumber.java.tr.Ozaman;
import io.cucumber.java.tr.Ve;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Senaryo adımları. Cucumber her senaryo için bu sınıftan yeni bir nesne oluşturur; alanlar o
 * senaryonun durumudur (hangi SKU, güncel ad/açıklama/fiyat).
 *
 * <p>Vitrin "kısa gecikmeyle" güncellenir (eventual consistency); bu yüzden vitrine dair her kontrol
 * bir süre bekleyerek tekrar dener.
 */
public class ProductSyncSteps {

    private static final Duration EVENTUALLY = Duration.ofSeconds(15);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {
            };

    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;

    @Before
    public void startSystem() {
        SystemUnderTest.start();
        sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @After
    public void restartStorefrontIfStopped() {
        SystemUnderTest.startStorefront(); // senaryo vitrin kapalıyken patladıysa sonrakiler etkilenmesin
    }

    // --- Backoffice: ürün yöneticisi ve depo görevlisi ---

    @Eğerki("ürün yöneticisi {string} adlı ürünü {int} {word} fiyatla oluşturur")
    public void createProduct(String name, int price, String currency) {
        this.name = name;
        this.description = name + " açıklaması";
        this.price = BigDecimal.valueOf(price);
        this.currency = currency;
        backoffice().post().uri("/products").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", sku, "name", name, "description", description, "price", price, "currency", currency))
                .retrieve().toBodilessEntity();
    }

    @Diyelimki("vitrinde {string} adlı ürün {int} {word} fiyatla var")
    public void productIsInStorefront(String name, int price, String currency) {
        createProduct(name, price, currency);
        await().atMost(EVENTUALLY).until(() -> storefrontItem().isPresent());
    }

    @Eğerki("ürün yöneticisi fiyatı {int} {word} yapar")
    public void changePrice(int price, String currency) {
        this.price = BigDecimal.valueOf(price);
        this.currency = currency;
        updateProduct();
    }

    @Eğerki("ürün yöneticisi açıklamayı {string} yapar")
    public void changeDescription(String description) {
        this.description = description;
        updateProduct();
    }

    @Eğerki("ürün yöneticisi ürünü satıştan kaldırır")
    public void deactivate() {
        backoffice().post().uri("/products/{sku}/deactivate", sku).retrieve().toBodilessEntity();
    }

    @Eğerki("depo görevlisi stoğu {int} olarak sayar")
    public void countStock(int quantity) {
        backoffice().put().uri("/products/{sku}/stock", sku).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity)).retrieve().toBodilessEntity();
    }

    private void updateProduct() {
        backoffice().put().uri("/products/{sku}", sku).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "description", description, "price", price, "currency", currency))
                .retrieve().toBodilessEntity();
    }

    // --- Storefront: vitrin ---

    @Ozaman("vitrinde ürün aktif ve {string} olarak görünür")
    public void itemIsActiveAndOutOfStock(String ignoredLabel) {
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(storefrontItem()).hasValueSatisfying(item -> {
            assertThat(item.get("active")).isEqualTo(true);
            assertThat(item.get("outOfStock")).isEqualTo(true);
            assertThat(item.get("name")).isEqualTo(name);
        }));
    }

    @Ozaman("vitrinde fiyat {int} {word} olur")
    public void priceIs(int price, String currency) {
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(storefrontItem()).hasValueSatisfying(item -> {
            assertThat(new BigDecimal(item.get("price").toString())).isEqualByComparingTo(BigDecimal.valueOf(price));
            assertThat(item.get("currency")).isEqualTo(currency);
        }));
    }

    @Ozaman("vitrinde açıklama {string} olur")
    public void descriptionIs(String description) {
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(storefrontItem())
                .hasValueSatisfying(item -> assertThat(item.get("description")).isEqualTo(description)));
    }

    @Ozaman("vitrinde stok {int} olur")
    public void stockIs(int stock) {
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(storefrontItem())
                .hasValueSatisfying(item -> assertThat(item.get("stock")).isEqualTo(stock)));
    }

    @Ozaman("ürün vitrinde listelenmez")
    public void notListed() {
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(storefront().get().uri("/catalog")
                .retrieve().body(JSON_ARRAY)).noneMatch(item -> sku.equals(item.get("sku"))));
        assertThat(storefrontItem()).hasValueSatisfying(item -> assertThat(item.get("active")).isEqualTo(false));
    }

    // --- Kesintiler ---

    @Ve("vitrin uygulaması kapalı")
    public void storefrontIsDown() {
        SystemUnderTest.stopStorefront();
    }

    @Ve("değişiklikler kuyruklarda birikir")
    public void changesWaitInQueues() {
        // Backoffice outbox'ı boşalttı; mesajlar kimse okumadığı için durable kuyruklarda bekliyor.
        await().atMost(EVENTUALLY).untilAsserted(() -> {
            assertThat(messageCount("storefront.catalog")).isGreaterThanOrEqualTo(2);
            assertThat(messageCount("storefront.stock")).isGreaterThanOrEqualTo(1);
        });
    }

    @Ve("vitrin uygulaması tekrar açılır")
    public void storefrontIsBack() {
        SystemUnderTest.startStorefront();
    }

    // --- Hatalı mesajlar (Backoffice bunları üretmez; RabbitMQ'ya doğrudan gönderiyoruz) ---

    @Eğerki("vitrine hiç ulaşmamış bir ürün için stok mesajı gelir")
    public void stockMessageForUnknownProduct() {
        publishRaw("StockUpdated", "stock.updated", "{\"sku\":\"%s\",\"quantity\":4,\"version\":1}".formatted(sku));
    }

    @Eğerki("ürün için stoğu {int} olan bozuk bir mesaj gelir")
    public void invalidStockMessage(int quantity) {
        publishRaw("StockUpdated", "stock.updated",
                "{\"sku\":\"%s\",\"quantity\":%d,\"version\":1}".formatted(sku, quantity));
    }

    @Ozaman("mesaj {int} denemeden sonra park kuyruğuna düşer")
    public void parkedAfterAttempts(int attempts) {
        Message parked = awaitParked("storefront.stock.dlq");
        assertThat(parked.getMessageProperties().<Long>getHeader("x-attempts")).isEqualTo(attempts);
    }

    @Ozaman("bozuk mesaj tekrar denenmeden park kuyruğuna düşer")
    public void parkedWithoutRetry() {
        parkedAfterAttempts(1);
    }

    // --- yardımcılar ---

    private static RestClient backoffice() {
        return RestClient.create(SystemUnderTest.backofficeUrl());
    }

    private static RestClient storefront() {
        return RestClient.create(SystemUnderTest.storefrontUrl());
    }

    private Optional<Map<String, Object>> storefrontItem() {
        try {
            return Optional.ofNullable(storefront().get().uri("/catalog/{sku}", sku).retrieve().body(JSON_OBJECT));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private static long messageCount(String queue) {
        return SystemUnderTest.rabbitAdmin().getQueueInfo(queue).getMessageCount();
    }

    private static void publishRaw(String type, String routingKey, String body) {
        SystemUnderTest.rabbit().send("backoffice.events", routingKey, MessageBuilder.withBody(body.getBytes(UTF_8))
                .setContentType("application/json").setType(type).setMessageId(UUID.randomUUID().toString()).build());
    }

    /** Park kuyruğu senaryolar arasında ortak; bu senaryonun SKU'sunu taşıyan mesajı arar. */
    private Message awaitParked(String queue) {
        AtomicReference<Message> found = new AtomicReference<>();
        await().atMost(EVENTUALLY).until(() -> {
            Message message;
            while ((message = SystemUnderTest.rabbit().receive(queue)) != null) {
                if (new String(message.getBody(), UTF_8).contains(sku)) {
                    found.set(message);
                    return true;
                }
            }
            return false;
        });
        return found.get();
    }
}
