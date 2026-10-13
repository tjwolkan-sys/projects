---
name: gbpd-runtime-state-audit
description: gbpowerdialer runtime state, counter, yerel bellek ve duplicate source-of-truth analizi. Active/ringing/in-call sayıları, agent size drift, Redis-vs-memory tutarsızlığı, yatay ölçek engelleri veya race condition incelenirken kullan.
---

# gbpd-runtime-state-audit

## Akış

1. İncelenen metric veya state field'ını netleştir.
2. Tüm writer ve reader noktalarını dosya referanslarıyla listele.
3. Her writer'ı trigger tipine göre sınıflandır: AMI event, Redis event, scheduler, producer/consumer, cleanup, logout, stop job, REST action.
4. Şu anki authoritative source'u ve türetilmiş/duplicate source'ları belirle.
5. Non-idempotent increment/decrement, missed event, duplicate event handling ve cleanup fallback yollarını ara.
6. Kısa vadede reconciliation veya idempotent transition öner.
7. State update veya reconciliation hot path'teyse `gbpd-call-flow-performance-guardrails` ile call/event başına maliyeti değerlendir.
8. Uzun vadede tek state owner ve yatay ölçek partition anahtarını öner.

## Bilinen Hotspot'lar

- `Campaign`: `agentsSize`, `availAgentsSize`, `activeCalls`, `ringingCalls`, `inCalls`, `originatedCalls`, `callsInProgress`.
- `ACDEngine`: `idleUsers`, `systemActiveUsers`, campaign user count hesapları.
- `UserLoginLogoutHandler`: login/logout counter mutation'ları.
- `UserSubscriber`: user status/session event'leri ve executor ordering.
- `ManagerConnectionDecorator`: AMI event -> counter transition'ları.
- `CallCleaner`, `CampaignStopJob`, connection loss cleanup.

## Çıktı Formatı

- State adı ve beklenen anlamı.
- Writer/reader tablosu.
- Mevcut failure mode'lar.
- En düşük riskli kısa vade düzeltme.
- Hot path maliyet notu.
- Yatay ölçek için uzun vade hedef.
