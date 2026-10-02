# Faz 2 — İnceleme Rehberi

Kodu Claude yazdı, sen inceliyorsun. Faz 1'deki gibi: her milestone önce `test:` (kırmızı), sonra
`feat:` (yeşil) commit'i. **Önce testi oku, sonra kodu.**

```bash
git log --oneline phase-2-hexagonal   # geçmiş
git show p2-m3                        # bir milestone'un yeşil commit'i
git diff p2-m2 p2-m3 --stat           # iki milestone arasında ne değişti
git diff p1-m5 p2-m6 --stat           # Faz 1'den Faz 2'ye
./mvnw verify                         # tüm testler (Docker açık olmalı)
open acceptance/target/cucumber-report.html
```

## Büyük resim

```
                 ┌──────────── backoffice ────────────┐            ┌──────────── storefront ────────────┐
  HTTP ─▶ adapter.in.web                                 │            │  adapter.in.messaging ◀── RabbitMQ   │
            │                                            │            │        │                             │
            ▼                                            │            │        ▼                             │
     application.port.in ◀─ ProductService               │            │  application.port.in ◀─ CatalogService
            │                InventoryService            │            │        │                             │
            ▼                     │                      │            │        ▼                             │
         domain ◀─────────────────┘                      │            │     domain ◀─────────┘               │
   (Product, Inventory)                                  │            │  (CatalogItem)                       │
            ▲                                            │            │        ▲                             │
     application.port.out                                │            │  application.port.out                │
            ▲                                            │            │        ▲                             │
  adapter.out.persistence ── PostgreSQL (product,        │            │  adapter.out.persistence ── PostgreSQL│
  adapter.out.outbox          inventory, outbox) ──relay─┼─▶ backoffice.events ─▶ storefront.catalog/stock  │
                 └───────────────────────────────────────┘            └──────────────────────────────────────┘
                                   ortak tek şey: contract (mesaj record'ları, tip adları, routing key'ler)
```

Bağımlılık okları hep içeriye, domain'e doğru. `HexagonalArchitectureTest` bunu her build'de kontrol eder.

---

## M0 — İskelet (`p2-m0`)
**Oku:** kök `pom.xml`, `*/pom.xml`, `TestcontainersConfiguration`
- Neden üç modül? `storefront` içinden `com.rabbitlab.backoffice...` import etmeyi dene: ne olur?
- `@ServiceConnection` Faz 1'deki elle kurulan `ConnectionFactory`'nin yerini nasıl alıyor?

## M1 — Backoffice domain (`p2-m1`)
**Oku:** `ProductTest`, `InventoryTest` → `Product`, `Inventory`, `Price`, `AggregateRoot`, `domain/event/*`
- Versiyonu artık kim artırıyor? Faz 1'de kim artırıyordu?
- Hiçbir şeyi değiştirmeyen `update` neden versiyon artırmıyor? Artırsaydı Storefront'ta ne olurdu?
- `Price` neden tutarı 2 ondalığa sabitliyor? `new BigDecimal("100").equals(new BigDecimal("100.00"))` ne döner?
- Stok neden ayrı aggregate? Fiyat güncellemesi ile stok sayımı aynı anda gelirse hangi satırlar kilitlenir?

**Kendine sor:** Domain event'i ile mesaj sözleşmesi neden ayrı? (`domain/event/ProductCreated` ↔ `contract/ProductCreatedMessage`)

## M2 — Use case'ler, persistence, outbox (`p2-m2`)
**Oku:** `ProductServiceTest`, `ProductPersistenceIT` → `ProductService`, `port/in/*`, `port/out/*`,
`JdbcProductRepository`, `JdbcEventOutbox`, `ContractMapper`, `V1__product_inventory_outbox.sql`
- `should_not_store_product_when_outbox_write_fails`: outbox olmasaydı "ürün kaydedildi ama mesaj gitmedi" nasıl olurdu?
- `should_reject_save_of_stale_copy`: optimistic locking olmasaydı Backoffice 90, vitrin 120 derdi. Adım adım anlat.
- Transaction neden `@Transactional` değil de bir port (`Transaction`)? Bedeli ne?
- `isNew()` ile `persistedVersion()` neden ayrı? (İpucu: commit mesajı ve entegrasyon testinin yakaladığı hata.)

**Kendine sor:** Sahte (in-memory) repository'lerle yazılan unit testler hangi hatayı yakalayamadı? Neden?

## M3 — Outbox relay (`p2-m3`)
**Oku:** `OutboxRelayIT`, `OutboxRelaySchedulingIT` → `OutboxRelay`, `OutboxRelayScheduler`
- Bir turda kaç gidiş-dönüş var? Faz 1'deki `waitForConfirmsOrDie`'ı her mesajda çağırmakla farkı ne?
- `FOR UPDATE SKIP LOCKED` olmasaydı iki instance ne yapardı? `SKIP LOCKED` olmadan sadece `FOR UPDATE` olsaydı?
- `should_publish_messages_behind_a_failing_one`: sıra garantisinden neden vazgeçebildik?
- Relay mesajı gönderdi, onay geldi, `published_at` yazılmadan çöktü. Ne olur? Kim telafi eder?

**Kendine sor:** "Exactly-once" neden yok? Biz onun yerine ne yapıyoruz?

## M4 — Storefront domain ve kalıcı katalog (`p2-m4`)
**Oku:** `CatalogItemTest`, `CatalogServiceTest`, `CatalogPersistenceIT` → `CatalogItem`, `CatalogUpdate`,
`CatalogService`, `JdbcCatalogRepository`
- Storefront'un `Sku`/`Price`'ı Backoffice'tekilerle aynı. Neden paylaşmıyoruz?
- `should_not_lose_updates_when_details_and_stock_change_concurrently`: `for update` silinince test 200 yerine 180 görüyordu. Kaybolan güncelleme hangisiydi?
- Backoffice'te optimistic, Storefront'ta pessimistic lock. Gerekçe `CatalogService` yorumunda; ikna oldun mu?
- `insertIfAbsent` neden "önce bak, yoksa ekle" değil de `ON CONFLICT DO NOTHING`?

## M5 — Storefront RabbitMQ girişi (`p2-m5`)
**Oku:** `CatalogMessagingIT` → `CatalogMessageListener`, `MessageTranslator`, `ProcessedMessages`, `StorefrontTopology`
- Faz 1'deki `StorefrontConsumer` ile karşılaştır: iş kuralı nereye gitti, RabbitMQ bilgisi nerede kaldı?
- Inbox ve versiyon kontrolü ikisi de tekrarları yakalıyor. `should_ignore_redelivered_message_with_same_id` ikisini nasıl ayırıyor?
- Inbox kaydı ve katalog güncellemesi neden aynı transaction'da? Ayrı olsaydı hangi sırayla ne bozulurdu?
- Negatif stoklu mesaj neden "bilinmeyen ürün" kontrolünden önce reddediliyor? (`CatalogUpdate` kayıtlarının kurucuları)

**Kendine sor:** `MessageTranslator`'a neden "anti-corruption layer" deniyor?

## M6 — REST ve kabul testleri (`p2-m6`)
**Oku:** `features/*.feature` → `ProductSyncSteps`, `SystemUnderTest`; `ProductControllerTest` → `ProductController`, `ErrorHandler`
- Senaryolar Backoffice'in veritabanına veya Storefront'un bean'lerine dokunuyor mu? Neden dokunmamalı?
- US5-1'de Storefront kapalıyken mesajlar nerede bekliyor? Hangi ayar onları koruyor?
- US5-2 ve US5-3 neden Backoffice'in REST'i yerine RabbitMQ'ya doğrudan mesaj atıyor?
- Neden `application.yml` yerine `backoffice.yml`? (`SystemUnderTest` ve yml başındaki not)
- Controller'da Bean Validation yok. Kurallar nerede çalışıyor?

## M7 — Mimari kurallar (`p2-m7`)
**Oku:** `HexagonalArchitectureTest`, `ContractIndependenceTest`
- `Product`'a `@Component` ekle, `./mvnw test -pl backoffice` çalıştır. Mesajı oku, geri al.
- Hangi kural derleyicinin zaten yakaladığı bir şeyi tekrar ediyor? Hangisi gerçekten yeni bir güvence?

---

## Faz 1'in acıtan noktaları → Faz 2'de ne oldu
| # | Faz 1'de | Faz 2'de | Nerede |
|---|---|---|---|
| 1 | Consumer hem RabbitMQ'yu hem iş kuralını biliyordu | Hata politikası adapter'da, kural domain'de | `CatalogMessageListener` ↔ `CatalogItem` |
| 2 | Katalog bellekteydi | PostgreSQL, satır kilidiyle | `JdbcCatalogRepository` |
| 3 | Backoffice'in modeli yoktu, versiyonu kimse yönetmiyordu | `Product` / `Inventory` aggregate'leri | `domain/product`, `domain/inventory` |
| 4 | Event = sözleşme = domain nesnesi | Üç ayrı şey, iki çevirici | `ContractMapper`, `MessageTranslator` |
| 5 | İki modlu consumer (`null` ile mod) | Tek politika | `CatalogMessageListener` |
| 6 | DB'ye yazıp mesaj gönderememe | Transactional outbox | `JdbcEventOutbox`, `OutboxRelay` |
| 7 | Her mesajda senkron confirm | Toplu gönderim, onaylar birlikte | `OutboxRelay.relayPending` |

---

## Bilinçli bırakılan "acıtan noktalar" (Faz 3'ün motivasyonu)
İncelerken fark et, listeye kendi bulduklarını ekle:

1. **Tören (boilerplate).** Dört use case arayüzü + dört komut record'u, çoğu tek satırlık iş için.
   Her port gerçekten bir şey kazandırıyor mu? Hangisi kazandırmıyor?
2. **Transaction sınırı ikiye bölündü.** Storefront'ta use case kendi transaction'ını açıyor, ama
   listener inbox için onu dıştan sarıyor. "Bir iş = bir transaction" kuralını kim koruyor?
3. **Exception'ların yeri tutarsız.** `ProductInactiveException` domain'de, `DuplicateSkuException`
   application'da, `ConcurrentUpdateException` `port.out`'ta. REST adapter'ı üçünü de biliyor.
4. **Domain nesnesi dışarı sızıyor.** `BrowseCatalogUseCase` `CatalogItem` döndürüyor; adapter
   isterse `changeStock(...)` çağırabilir. Okuma için ayrı bir model (query side) gerekir mi?
5. **Tablolar sonsuza kadar büyüyor.** Yayınlanmış outbox satırları ve `processed_message` hiç
   silinmiyor. Ne zaman ve kim temizlemeli? Inbox'tan silinen bir id tekrar gelirse?
6. **Park kuyruğu çıkmaz sokak.** DLQ'daki mesajı düzeltip tekrar göndermenin yolu yok.
7. **Sözleşme sürümü yok.** `ProductCreatedMessage`'a zorunlu alan eklersek eski Storefront ne yapar?
   Outbox'ta bekleyen eski formattaki satırlar?
8. **Polling gecikmesi.** Relay 500 ms'de bir bakıyor: gecikme ile veritabanı yükü arasında bir takas.
9. **Hexagon ile katmanlar arasındaki fark ince.** `domain` / `application` / `adapter` paketleri
   zaten katman gibi duruyor. Onion (Faz 3) bunu nasıl farklı çizer?

## Senin listen
- [ ] ...
