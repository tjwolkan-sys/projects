---
name: gbpd-redis-contracts
description: gbpowerdialer Redis channel, pub/sub message, user/session status, predictive event, call event ve Redis-backed user data değişikliklerinde kullan. RedisUtil, publisher/subscriber, RedisMessage DTO'ları, Constants.REDIS_CHANNEL veya Redis state scaling değişirken kullan.
---

# gbpd-redis-contracts

## Akış

1. Channel, payload class, publisher ve subscriber'ı netleştir.
2. Versioning eklenmedikçe channel adını ve payload field'larını koru.
3. Mesajın "en fazla bir kez işlenir", "tekrara açık" veya "sıra duyarlı" sınıflarından hangisine girdiğini belirle.
4. Redisson listener thread'inde ağır iş yapma; bounded executor kullanımını bilerek seç.
5. Yeni üretim yolunda Redis `KEYS` kullanma. İndeks set/hash veya `SCAN` tercih et.
6. Call event veya user/session high-volume akışı etkileniyorsa `gbpd-call-flow-performance-guardrails` ile event başına maliyeti yaz.
7. Payload değişirse serialization/contract test ekle.

## Hotspot'lar

- `Constants.REDIS_CHANNEL`
- `RedisUtil.subscribe`, `publishMessage`, `getLiveUserIds`, `getUsersStatusBulk`
- `UserSubscriber`, `WebphoneSubscriber`, `ResourceDistributionServiceSubscriber`
- `CallEventPublisher`, `PredictivePublisher`, `InteractionNotifierPublisher`
- `redis/message/*`, `redis/event/*`

## Ölçek Kuralları

- `userData*` taraması yerine `liveUsers` benzeri açık indeks tasarla.
- Event handler'ları mümkün olduğunca idempotent yap.
- Authoritative state ile read-model/cache state'i ayır.
- Static DTO veya shared mutable serialization buffer kullanma.
- High-volume subscriber içinde blocking network, DB query veya geniş payload log'u ekleme.
