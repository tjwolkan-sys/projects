---
name: gbpd-go-migration-slice
description: gbpowerdialer'da Java monolith'ten Go servislerine güvenli migration tekniği. Strangler pattern, adapter, shadow read/write, dual-run, feature flag, contract test, state ownership, Docker hazırlığı veya Java davranışını kırmadan servis çıkarma gerektiğinde kullan.
---

# gbpd-go-migration-slice

## Amaç

Bu skill roadmap tutmaz. Roadmap `docs/project/GO-MICROSERVICE-ROADMAP.md` dosyasındadır. Bu skill, herhangi bir parçayı Go'ya taşırken canlı Java davranışını kırmadan nasıl teknik olarak ilerleneceğini tarif eder.

## Akış

1. Taşınmak istenen capability'yi netleştir; package taşımaya değil davranış boundary'sine odaklan.
2. Mevcut Java owner'larını, veri kaynaklarını, Redis/AMI/DB contract'larını ve yan etkileri listele.
3. İlk Go sürümünün modunu seç: read-only, shadow-read, shadow-write, dual-run veya authoritative.
4. Java tarafında compatibility adapter bırak; yeni servis kanıtlanmadan eski path'i kaldırma.
5. Özellik bayrağı ve rollback planını implementation'dan önce yaz.
6. Contract testlerini Java payload/action/event örnekleriyle kur.
7. Observability ekle: health, readiness, metrics, structured log, event lag, reconciliation diff.
8. Write ownership değişecekse önce idempotency ve reconciliation hazır olsun.

## Teknik Kurallar

- External boundary'lere interface koy: AMI, Redis, DB, scheduler clock, event bus.
- Go servisi config'i env/secret manager üzerinden alsın; dosyaya secret yazma.
- Her consumer idempotent olmalıdır; duplicate event zarar vermemeli.
- Partition key açık olmalı: campaign, server, tenant veya shard.
- Counter'ları state'ten türet; atomic counter gerekiyorsa reconcile job tasarla.
- Graceful shutdown'da in-flight command/event drain et.
- Timeout, retry ve circuit breaker davranışı explicit olsun.

## Java ile Birlikte Yaşama

- Java monolith ilk etapta source-of-truth kalabilir; Go servis önce gözlemci olur.
- Go servis sonucu Java sonucu ile karşılaştır; farkları metric ve log olarak yayımla.
- Cutover tek kampanya, tek server veya tek tenant gibi dar bir shard ile yapılır.
- Rollback, feature flag ile eski Java path'e dönerek yapılabilmelidir.
- Shared DB kullanılıyorsa write conflict ve transaction ownership net yazılır.

## Çıktı Formatı

Migration tasarımı istenirken şunları ver:

- Capability boundary'si.
- Java owner dosyaları.
- Contract'lar ve payload/event örnekleri.
- İlk çalışma modu.
- Test ve shadow validation planı.
- Rollback planı.
- Docker/operasyon gereksinimleri.
