# Faz 2 — Plan: DDD + Hexagonal

Faz 1'in "acıtan noktaları"nı ([faz-1-inceleme.md](faz-1-inceleme.md)) sırayla çözüyoruz.
Çalışma şekli aynı: her milestone önce `test:` (kırmızı), sonra `feat:` (yeşil) commit'i; sonunda `p2-mN` tag'i.

## Kararlar
| Karar | Seçim | Gerekçe |
|---|---|---|
| Framework | Spring Boot 4 (AMQP, JDBC, Flyway, Web) | Gerçek dünyaya yakın. Spring sadece adapter ve wiring katmanında; domain framework'süz |
| Aggregate'ler | `Product` ve `Inventory` ayrı | İki ayrı aktör (ürün yöneticisi / depo), iki ayrı versiyon sayacı, iki ayrı tutarlılık sınırı |
| Yapı | Çok modüllü Maven: `contract`, `backoffice`, `storefront` | Bounded context sınırını derleyici korur; iki context birbirinin sınıfını göremez |
| Veritabanı | PostgreSQL, her context'in kendi şeması/DB'si | Storefront'un vitrini artık kalıcı (acıtan nokta 2) |
| Mesaj sözleşmesi | `contract` modülünde ayrı record'lar | Domain event ≠ mesaj sözleşmesi (acıtan nokta 4) |
| Güvenilir yayın | Transactional Outbox | DB'ye yazıp mesaj gönderememe sorunu (acıtan nokta 6) |

## Modüller ve paketler
```
contract/     com.rabbitlab.contract           mesaj record'ları, tip adları, routing key'ler, exchange adı
backoffice/   com.rabbitlab.backoffice
                ├─ domain                       Product, Inventory, domain event'leri (saf Java)
                ├─ application                  use case'ler
                │    └─ port.in / port.out      inbound / outbound port arayüzleri
                └─ adapter
                     ├─ in.web                  REST controller
                     └─ out.persistence|messaging  JDBC repository, outbox, RabbitMQ relay
storefront/   com.rabbitlab.storefront
                ├─ domain                       CatalogItem (saf Java)
                ├─ application(.port)           ürün event'ini uygula use case'i
                └─ adapter
                     ├─ in.messaging            RabbitMQ listener (idempotent, retry/DLQ)
                     └─ out.persistence         JDBC catalog repository
```

## Milestone'lar
| # | İçerik | Çözdüğü acıtan nokta |
|---|---|---|
| M0 | Çok modüllü iskelet, Spring Boot, Testcontainers (PostgreSQL + RabbitMQ) ile context yükleme testi | — |
| M1 | Backoffice domain: `Product` ve `Inventory` aggregate'leri, kurallar, versiyon artışı, domain event'leri (saf unit test) | 3 |
| M2 | Backoffice application + persistence: use case'ler, JDBC repository, outbox tablosuna aynı transaction'da yazma | 6 |
| M3 | Outbox relay: outbox → `contract` mesajı → RabbitMQ (confirm + mandatory), at-least-once | 4, 6 |
| M4 | Storefront domain + persistence: `CatalogItem`, versiyon kontrollü kalıcı katalog | 2 |
| M5 | Storefront inbound adapter: idempotent consumer, retry/DLQ politikası adapter'da, domain RabbitMQ'yu bilmez | 1, 5 |
| M6 | REST inbound adapter + US1–US5 Cucumber senaryoları uçtan uca | — |
| M7 | ArchUnit ile hexagon kuralları (domain → hiçbir şeye bağımlı değil) + Faz 2 inceleme rehberi | — |

Acıtan nokta 7 (senkron confirm) M3'te outbox relay'in toplu gönderimiyle ele alınır.
