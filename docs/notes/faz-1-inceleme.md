# Faz 1 — İnceleme Rehberi

Kodu Claude yazdı, sen inceliyorsun. Her milestone iki commit'ten oluşur:
önce `test:` (kırmızı — ne istediğimizi söyler), sonra `feat:` (yeşil — nasıl yaptığımızı gösterir).
**Önce testi oku, sonra kodu.** Senior'lar PR'a hep testlerden başlar.

```bash
git log --oneline phase-1-basics      # geçmiş
git show p1-m2                        # bir milestone'un yeşil commit'i
git diff p1-m1 p1-m2                  # iki milestone arasındaki fark
./mvnw test   # veya IntelliJ: src/test/java → sağ tık → Run 'All Tests'
```

> Testler Docker ister (OrbStack açık olmalı). İlk çalıştırmada RabbitMQ imajı indirilir, 1-2 dk sürebilir.

---

## M0 — İskelet (`p1-m0`)
**Oku:** `RabbitMqTestSupport`, `RabbitMqConnectionTest`
- Singleton container: neden her test için yeni bir RabbitMQ açmıyoruz?
- `uniqueName(...)`: testler aynı broker'ı paylaşırken neden çakışmıyor?
- `setAutomaticRecoveryEnabled(false)` testte neden kapalı?

**Kendine sor:** Connection ile channel arasındaki fark ne? Neden bir channel'ı iki thread paylaşmamalı?

## M1 — Hello Queue (`p1-m1`)
**Oku:** `HelloQueueTest`, `CatalogTest`, `MessageCodecTest`, `ProductSyncFlowTest` → `event/*`, `Catalog`, `CatalogItem`, `MessageCodec`
- Event'ler record ve kurucularında doğrulama var. Negatif stoklu bir `StockUpdated` oluşturulabilir mi?
- `CatalogItem` iki versiyon tutuyor. `CatalogTest.should_apply_stock_when_details_version_is_higher_than_stock_version` testini oku: tek versiyon olsaydı ne bozulurdu?
- Mesaj tipi neden gövdede değil de AMQP `type` özelliğinde?

**Kendine sor:** Default exchange nedir? "Kuyruğa gönderdim" demek neden teknik olarak yanlış?

## M2 — Work Queue (`p1-m2`)
**Oku:** `AcknowledgementTest`, `PrefetchTest`, `StockWorkQueueTest` → `StorefrontConsumer`, `ProductEventHandler`
- `should_lose_message_when_auto_ack_consumer_dies` ile `should_redeliver_message_when_consumer_dies_before_ack` arasındaki tek fark ne?
- `should_end_with_latest_stock_when_two_instances_process_out_of_order`: sıra bozulduğu halde sonuç neden doğru?
- Consumer'da üç farklı tepki var (ack / reject / nack+requeue). Hangisi ne zaman?

**Kendine sor:** At-least-once ne demek? Storefront neden idempotent olmak zorunda?

## M3 — Exchange'ler (`p1-m3`)
**Oku:** `ExchangeTypesTest`, `BackofficeTopologyTest` → `Topology`, `EventRouting`, `BackofficePublisher`
- `product.*` ve `product.#` farkını test hangi routing key ile kanıtlıyor?
- `should_deliver_to_new_listener_without_changing_publisher`: bu neden mimari olarak önemli?
- `should_sync_catalog_end_to_end...` testindeki "DİKKAT" yorumunu oku: iki kuyruk arasında sıra garantisi var mı?

**Kendine sor:** Publisher kuyrukları biliyor mu? Bilmemesinin faydası ne?

## M4 — Güvenilirlik (`p1-m4`)
**Oku:** `PublisherConfirmTest`, `BrokerRestartTest`, `ReliablePublisherTest` → `BackofficePublisher`, `PublishFailedException`
- `BrokerRestartTest`: durable kuyruk tek başına mesajı korudu mu?
- Publisher'da `mandatory=true` olmasaydı `should_fail_loudly...` testi ne yapardı? (İpucu: `ExchangeTypesTest.should_silently_drop...`)
- Her mesajda confirm beklemenin bedeli ne? Yüksek hacimde ne yapılır?

**Kendine sor:** "Mesaj kaybolur mu?" sorusuna üç katmanlı cevap ver: publisher → broker → consumer.

## M5 — Hata Yönetimi (`p1-m5`)
**Oku:** `DeadLetterTest`, `StockRetryTest`, `RetryCountTest` → `Topology` (retry/dlq), `StorefrontConsumer.withRetry`
- Mesajın yolculuğunu çiz: `storefront.stock` → `.retry` → `storefront.stock` → … → `.dlq`
- Deneme sayısı nereden okunuyor? (`x-death`)
- Bozuk mesaj neden hiç tekrar denenmiyor da doğrudan parka gidiyor?
- Requeue yerine neden "bekleme odası" (TTL kuyruğu)?

**Kendine sor:** Sonsuz requeue bir sistemi nasıl kilitler?

---

## Bilinçli bırakılan "acıtan noktalar" (Faz 2'nin motivasyonu)
Faz 1 kodu bilerek düz. İncelerken bunları fark et, listeye kendi bulduklarını ekle:

1. **İş kuralı ile altyapı iç içe.** `StorefrontConsumer` hem RabbitMQ'yu (ack, x-death) hem hata politikasını biliyor.
2. **Catalog bellekte.** Uygulama kapanınca vitrin boşalır; iki instance gerçekte ayrı bellek tutardı.
3. **Backoffice'in kendi modeli yok.** Event'leri testler elle oluşturuyor; versiyonu kim artırıyor, kural nerede?
4. **Event = mesaj sözleşmesi = domain nesnesi.** Aynı record hem iş kavramı hem JSON formatı. Alan adı değişirse ne olur?
5. **İki modlu consumer** (`parkingQueue == null` kontrolü). Null ile mod seçmek kırılgan.
6. **DB'ye yazıp mesaj gönderememe** durumu yok sayıldı (Backoffice'te DB yok). Faz 2'de Outbox ile çözülecek.
7. **Her mesajda senkron confirm** — basit ama yavaş.

## Senin listen
- [ ] ...
