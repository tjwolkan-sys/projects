---
name: gbpd-campaign-lifecycle-flow
description: gbpowerdialer campaign lifecycle değişikliklerinde kullan. DialerFacade, CampaignContext, CampaignStateTemplate, StartState, PauseState, StopState, CampaignStopJob, ScheduledCampaignRunner, CampaignSchedulerOrchestrator, schedule chain, memento, start/pause/stop/reload/reset veya scheduler-manuel aksiyon yarışı değişirken kullan.
---

# gbpd-campaign-lifecycle-flow

## Amaç

Campaign lifecycle outbound çağrı akışının ana kontrol noktasıdır. Start, pause, stop, reload, reset, scheduler ve stop job değişiklikleri queue, counter, DB, Redis, AMI ve operatör davranışını birlikte etkileyebilir.

## Birlikte Kullanılacak Skill'ler

- Kod değişikliği varsa önce `gbpd-tdd-workflow`.
- Davranış belirsizse `gbpd-characterization-tests`.
- Counter, local queue veya JVM state etkileniyorsa `gbpd-runtime-state-audit`.
- AMI originate/hangup/result etkileniyorsa `gbpd-ami-event-flow`.
- Redis publish/subscribe veya payload etkileniyorsa `gbpd-redis-contracts`.
- Producer/consumer/originator loop, scheduler tick veya call event hot path etkileniyorsa `gbpd-call-flow-performance-guardrails`.

## Önce Oku

1. `docs/architecture/CAMPAIGN-LIFECYCLE.md`
2. `docs/architecture/DIALER-RUNTIME.md`
3. Gerekirse `docs/architecture/AGENT-ACD-AND-COUNTERS.md`

## Kritik Kod Yüzeyi

- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/DialerFacade.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CampaignContext.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/state/*`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/CampaignStopJob.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/scheduler/ScheduledCampaignRunner.java`
- `gbpowerdialer/src/main/java/com/powerdialer/engine/scheduler/schedulechain/**`
- `CampaignMemento` ve `CampaignMementoService`

## Akış Haritalama

Değişiklikten önce ilgili kampanya için şu tetikleyicileri çıkar:

1. Manuel UI/API aksiyonu.
2. Scheduler aksiyonu.
3. Redis entity/user/session event'i.
4. Stop condition veya end-list behavior.
5. Asterisk/AMI event veya originate cleanup yan etkisi.

Her tetikleyici için şu sırayı yaz:

```text
trigger -> DialerFacade -> CampaignContext -> state method -> producer/consumer/originator -> DB/Redis/counter cleanup
```

## Kontrol Listesi

- `campaignContexts` ekleme, okuma ve kaldırma noktalarını kontrol et.
- Start, pause, unpause, stop regular, stop forceful, reload ve reset davranışını birlikte düşün.
- `CampaignStopJob` başlatma, bekleme, publish/update ve context cleanup sırasını bozma.
- Memento save, restore ve reset sırasını değiştirirken eski counter değerlerini kontrol et.
- Producer, consumer ve originator queue temizliğinin fetched flag reset'iyle uyumlu olduğunu doğrula.
- Stop/reload/reset sırasında active, ringing, in-call, agent ve list size counter'larının writer/readers noktalarını çıkar.
- Scheduler kararı ile manuel aksiyon aynı kampanyaya temas ediyorsa race senaryosunu açık yaz.
- End-list behavior, retry, callback, DNC, LSC ve predictive yan etkilerini kontrol et.
- Producer/consumer/originator döngülerinde call başına DB query, tam liste taraması veya geniş lock ekleme.

## Test Stratejisi

1. Önce state transition karakterizasyon testi yaz.
2. Scheduler karar mantığı değişiyorsa schedule chain contract testi ekle.
3. Stop/reload/reset değişiyorsa regression testi ile queue cleanup, memento ve fetched flag davranışını kilitle.
4. Async davranışta sleep'e dayalı test yazma; executor, clock, callback veya doğrudan service boundary kullan.
5. Gerçek AMI, Redis veya DB bağlantısı kurma; mock, fake veya küçük pure function boundary kullan.

## Raporlama

- Hangi lifecycle aksiyonu değiştiğini açık yaz.
- Etkilenen queue, counter, memento, DB ve Redis/AMI yan etkilerini özetle.
- Hot path etkisi varsa call/tick başına ek maliyet ve azaltımı yaz.
- Çalıştırılan hedefli testleri ve kalan race risklerini belirt.
