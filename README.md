# rabbit-lab

Backoffice → E-ticaret (Storefront) ürün ve stok senkronizasyonu üzerinden
RabbitMQ, DDD, Hexagonal ve Onion mimarisini TDD ile öğrenme projesi.

| Branch | İçerik |
|---|---|
| `main` | Senaryo, sözlük, docker-compose |
| `phase-1-basics` | Faz 1 — saf `amqp-client` ile RabbitMQ temelleri |
| `phase-2-hexagonal` | Faz 2 — DDD + Hexagonal (yakında) |
| `phase-3-onion` | Faz 3 — Onion (yakında) |

- Senaryo: [docs/scenario.md](docs/scenario.md)
- Sözlük: [docs/glossary.md](docs/glossary.md)

## Gereksinimler
- Java 21
- Docker (OrbStack) — testler Testcontainers ile gerçek RabbitMQ'ya karşı koşar
- Maven (IntelliJ'nin gömülü Maven'ı yeterli)

## Yerel RabbitMQ
```bash
docker compose up -d
# http://localhost:15672  rabbitlab / rabbitlab
```
