# rabbit-lab

Backoffice → E-ticaret (Storefront) ürün ve stok senkronizasyonu üzerinden
RabbitMQ, DDD, Hexagonal ve Onion mimarisini TDD ile öğrenme projesi.

| Branch | İçerik |
|---|---|
| `main` | Senaryo, sözlük, docker-compose |
| `phase-1-basics` | Faz 1 — saf `amqp-client` ile RabbitMQ temelleri |
| `phase-2-hexagonal` | Faz 2 — DDD + Hexagonal, Spring Boot, PostgreSQL, Outbox (devam ediyor) |
| `phase-3-onion` | Faz 3 — Onion (yakında) |

- Senaryo: [docs/scenario.md](docs/scenario.md)
- Sözlük: [docs/glossary.md](docs/glossary.md)
- Faz 2 planı: [docs/notes/faz-2-plan.md](docs/notes/faz-2-plan.md)

## Gereksinimler
- Java 21
- Docker (OrbStack) — testler Testcontainers ile gerçek RabbitMQ ve PostgreSQL'e karşı koşar
- Maven kurulu olmak zorunda değil: `./mvnw verify`

## Modüller
| Modül | İçerik |
|---|---|
| `contract` | Backoffice → Storefront mesaj sözleşmesi (bağımlılıksız) |
| `backoffice` | Ürün ve stoğun asıl kaydı; outbox ile yayın (port 8081) |
| `storefront` | Vitrin; event'leri tüketip kalıcı kataloğa uygular (port 8082) |

## Yerel altyapı
```bash
docker compose up -d
# RabbitMQ: http://localhost:15672  rabbitlab / rabbitlab
# PostgreSQL: localhost:5432  rabbitlab / rabbitlab  (veritabanları: backoffice, storefront)
```
