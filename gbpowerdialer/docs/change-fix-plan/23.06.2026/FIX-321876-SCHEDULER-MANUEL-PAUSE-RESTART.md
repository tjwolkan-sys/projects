# FIX-321876 Scheduler Manuel Pause Restart Change/Fix Planı

- İş Etiketi: fix
- Kimlik: 321876
- Slug: scheduler-manuel-pause-restart
- Tarih: 2026-06-23
- Öncelik: normal
- Plan Durumu: Uygulandı ve hedefli testler yeşil (2026-06-23)

## Bağlam

`CampaignScheduler` her `campaign.schedule.delay` (üretimde `application.properties:52` = `60000` ms, yani dakikada bir) `ScheduledCampaignRunner.processScheduledCampaigns()` çalıştırır. Operatör schedule penceresi (from–till) içindeyken bir kampanyayı manuel olarak **pause** ettiğinde, bir sonraki scheduler tick'inde (≤1 dk) kampanya kendiliğinden tekrar **start** oluyor. Operatörün manuel duraklatma kararı, scheduler tarafından sessizce geri alınıyor.

Beklenen davranış: Manuel pause kalıcı (sticky) olmalı; operatör tekrar açana (unpause) veya durdurana kadar scheduler manuel paused kampanyayı yeniden başlatmamalı. Otomasyon (schedule automation) kaynaklı pause sonrası koşul kalkınca devam etme davranışı korunmalı.

Bu plan, kullanıcı ile yapılan analiz tartışmasında onaylanan **Yaklaşım A — manuel/otomasyon pause ayrımı** kararını uygular.

## İş Etiketi

fix

## Öncelik

normal

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/scheduler/ScheduledCampaignRunner.java`
  - `shouldStartCampaign` — PAUSED state'inin start kararına girişi.
- `gbpowerdialer/src/main/java/com/powerdialer/service/CampaignService.java`
  - `action(...)` `PAUSE` case'i — UI'dan gelen `stopType=MANUAL` sinyalinin pause yolunda kullanılması.
- `gbpowerdialer/src/main/java/com/powerdialer/engine/dialer/DialerFacade.java`
  - `pauseCampaign`, `unpauseCampaign`, `startCampaign`, `shutdownCampaign` — manuel-pause işaretinin set/clear noktaları.
- `gbpowerdialer/src/main/java/com/powerdialer/model/Campaign.java` (veya `CampaignContext`)
  - Yeni transient `manuallyPaused` işareti (kalıcılık kararı Açık Sorular #1'e bağlı).
- `gbpowerdialer/src/test/java/com/powerdialer/engine/scheduler/ScheduledCampaignRunnerTest.java` (genişletilir)
- `gbpowerdialer/src/test/java/tests/<domain>` altında gerekiyorsa dar regression/servis testi.

## Mevcut Davranış

### 1) Canlı scheduler yolu PAUSED'ı yeniden başlatılabilir kabul ediyor

`ScheduledCampaignRunner.processCampaignActions` öncelik sırası: stop → reset → start → pause. Manuel paused kampanyada `mustBeStopped=false` olduğundan start adımına gelinir:

```java
// ScheduledCampaignRunner.java:120-123
private boolean shouldStartCampaign(ScheduleCheckResult result, Constants.CAMPAIGN_STATE state) {
    return result.isMustBeStarted()
        && (state == STOPPED || state == STOPPING || state == PAUSED);  // <-- PAUSED burada
}
```

Schedule penceresi içinde `ScheduleTimeCheckHandler.evaluateScheduleTimeFrame` her tick `mustBeStarted=true` üretir (`ScheduleTimeCheckHandler.java:51-52`). State PAUSED olduğu için `shouldStartCampaign` true döner → `startCampaign()` (`ScheduledCampaignRunner.java:92-95`, `169-172`) → kampanya yeniden başlar.

### 2) Eski (dormant) scheduler yolu PAUSED'ı dışlıyordu

`AutomaticCampaignExecutor.startCampaign` paused kampanyayı asla başlatmıyordu:

```java
// AutomaticCampaignExecutor.java:64-67
if (!checker.isMustBeStarted() || ... || campaignState == CAMPAIGN_STATE.PAUSED || ...)
    return;
```

`AutomaticCampaignExecutor.iterateCampaigns()` repoda hiçbir yerden çağrılmıyor (yalnız `CampaignScheduler:30` autowire ediliyor, tick'te kullanılmıyor). Yani bu davranış değişikliği eski yoldan yeni chain tabanlı `ScheduledCampaignRunner`'a geçişte (PAUSED start setine eklenerek) ortaya çıkmış bir regresyondur.

### 3) Manuel pause "MANUAL" sinyalini taşıyor ama pause yolunda düşürülüyor

UI/API generic action endpoint'i her aksiyonu `MANUAL` ile gönderir:

```java
// CampaignController.java:130-131
public @ResponseBody void action(@RequestParam String campaignId, @RequestParam CAMPAIGN_ACTION type) {
    ((CampaignService) getService()).action(Long.valueOf(campaignId), type, Constants.CAMPAIGN_STOP_TYPE.MANUAL);
}
```

`CampaignService.action`:

```java
// CampaignService.java:1012-1026
case START:                       // updateStopType(null)  -> manuel STOP işareti TEMİZLENİR
  ...
  updateStopType(campaignId, null);
  break;
case STOP_REGULAR: ...            // updateStopType(stopType) -> MANUAL DB'ye YAZILIR
  updateStopType(campaignId, stopType);
  break;
case PAUSE:
  dialerFacade.pauseCampaign(campaignId);   // <-- stopType (MANUAL) parametresi TAMAMEN YOK SAYILIR
  break;
```

Manuel STOP'ta `stopType=MANUAL` DB'ye yazılır ve `SchedulePreCheckHandler.isManuallyStopped` (`SchedulePreCheckHandler.java:22-31`) zinciri kısa devre yaparak manuel durdurulanı yeniden başlatmaz. PAUSE'ta ise aynı `MANUAL` sinyali atılır; manuel pause için hiçbir koruyucu işaret kalmaz. (`SchedulePreCheckHandler.java:18`'deki `//TODO : should if condition have isStoppedbyCondition check also?` notu da bu boşluğa işaret eder.)

### Otomasyon pause yolu (korunacak davranış)

Scheduler otomasyon pause'u `action(id, PAUSE, null)` ile gelir (`ScheduledCampaignRunner.java:181`). `AutomationCheckHandler` pause-automation true ise `mustBePaused=true` + `mustBeStarted=false` set eder (`AutomationCheckHandler.java:49-59`). Pause koşulu sürdükçe state PAUSED kalır (`shouldPauseCampaign` RUNNING ister); koşul kalkınca `mustBeStarted=true` olur ve kampanya START ile devam eder. Bu auto-resume davranışı **korunmalıdır**.

## Beklenen Davranış

- Manuel pause (UI/API kaynaklı) yapılan kampanya, schedule penceresi içinde olsa bile scheduler tarafından otomatik START edilmez; operatör unpause/start/stop yapana kadar PAUSED kalır.
- Otomasyon (schedule automation) kaynaklı pause sonrası mevcut auto-resume (pause koşulu kalkınca START) davranışı aynen korunur.
- Manuel-pause işareti operatör unpause, manuel start veya stop yaptığında temizlenir; sonraki scheduler kararlarını engellemez.
- Schedule penceresi bittiğinde (`now >= till` → `mustBeStopped`) davranış değişmez: manuel paused kampanya gün sonu otomatik STOP edilmeye devam eder (bu fix yalnızca istenmeyen RESTART'ı engeller, gün-sonu STOP'u kaldırmaz — bkz. Açık Sorular #3).
- Redis channel adları, AMI event adları, campaign state transition adları ve scheduler tick periyodu değişmez.

## Kod Okuma Bulguları

- `ScheduledCampaignRunner.getCampaignState` (`:200-203`) state'i **in-memory** `CampaignContext.getCampaign().getState()`'ten okur; chain ise `campaign`'i `getCampaignsWithSchedules()` (DB) listesinden alır. Manuel-pause guard'ı runner içinde (in-memory context üzerinde) uygulanacağı için işaret de in-memory context campaign üzerinde tutulabilir; bu sayede DB migration gerekmez.
- `DialerFacade.pauseCampaign/unpauseCampaign/startCampaign/shutdownCampaign` hepsi `campaignContexts.get(campaignId).getCampaign()` aynı in-memory nesnesi üzerinde çalışır (`DialerFacade.java:433-472`); işaret set/clear için tutarlı tek nokta.
- `pauseCampaign` yalnız `state == RUNNING` iken pause uygular (`:467`). İşaret set'i bu guard'la uyumlu yapılmalı.
- `CampaignService.action` `synchronized` (`:1010`); manuel pause ile scheduler kararı aynı kampanyaya temas etse de action'lar serialize. Scheduler state'i okurken (`getCampaignState`) action'ın ortasına denk gelebilir; işaret set'i pause state geçişiyle aynı sıraya konmalı (race notu için bkz. Riskler).
- PAUSE action'ının tek otomatik çağıranı scheduler (`null` stopType), tek manuel çağıranı UI generic endpoint (`MANUAL` stopType). Repo genelinde başka `action(..., PAUSE, ...)` çağıranı yok → `stopType==MANUAL` manuel-pause için güvenilir ayraç.

## Root Cause / Değişiklik Gerekçesi

İki yapısal eksik birlikte bugu üretiyor:

1. **Yeni chain scheduler PAUSED'ı start setine ekledi** (`shouldStartCampaign`), eski `AutomaticCampaignExecutor` ise dışlıyordu. Bu, otomasyon-pause auto-resume'unu mümkün kılmak için gerekli ama manuel ve otomasyon pause'u ayırt etmeden uygulandığı için manuel pause'u da geri açıyor.
2. **`CampaignService.action` PAUSE case'i, UI'dan gelen `MANUAL` sinyalini düşürüyor.** Manuel pause ile otomasyon pause'u ayırt edecek veri sisteme zaten akıyor; pause yolunda kullanılmıyor.

Kök-neden çözümü: pause kaynağını (manuel/otomasyon) type-safe biçimde işaretlemek ve scheduler START kararında manuel paused kampanyayı dışlamak. Bu, mevcut manuel-STOP (`stopType=MANUAL` + PreCheck short-circuit) desenini pause için tutarlı biçimde tamamlar; case-specific patch değil owner boundary çözümüdür.

## Çözüm Yaklaşımı

### (A) Manuel-pause işareti

`Campaign` (in-memory context nesnesi) üzerine type-safe bir işaret eklenir. Tercih edilen ad: `manuallyPaused` (boolean). Set/clear noktaları:

```
DialerFacade.pauseCampaign(campaignId, boolean manual):
   state == RUNNING iken pause uygulanırken campaign.setManuallyPaused(manual)
DialerFacade.unpauseCampaign(campaignId):
   campaign.setManuallyPaused(false)   // operatör tekrar açtı
DialerFacade.startCampaign(...):
   campaign.setManuallyPaused(false)   // start manuel/otomasyon farketmeksizin işareti sıfırlar
DialerFacade.shutdownCampaign(...):
   campaign.setManuallyPaused(false)   // stop sonrası işaret anlamsız
```

`CampaignService.action` PAUSE case'i:

```java
case PAUSE:
    dialerFacade.pauseCampaign(campaignId, Constants.CAMPAIGN_STOP_TYPE.MANUAL.equals(stopType));
    break;
```

- UI/API pause → `stopType=MANUAL` → `manual=true`.
- Scheduler otomasyon pause → `stopType=null` → `manual=false`.

### (B) Scheduler START kararı

`ScheduledCampaignRunner.shouldStartCampaign` PAUSED'ı yalnız manuel olmayan (otomasyon) pause'da başlatacak şekilde daraltılır:

```java
// PAUSED yalnız manuallyPaused=false iken startable
return result.isMustBeStarted()
    && (state == STOPPED || state == STOPPING
        || (state == PAUSED && !isManuallyPaused(campaign)));
```

`isManuallyPaused`, state ile aynı kaynaktan (in-memory context campaign) okunur; context yoksa (STOPPED) zaten PAUSED dalına girilmez. STOP/RESET/PAUSE kararları değişmez — bu fix yalnız istenmeyen RESTART'ı engeller.

### Kapsam dışı (bu planda değiştirilmez)

- Otomasyon auto-resume'un `CAMPAIGN_ACTION.START` mı `UNPAUSE` mı olması gerektiği (bkz. Açık Sorular #2).
- Gün-sonu (`mustBeStopped`) STOP davranışı (bkz. Açık Sorular #3).
- Eski dormant `AutomaticCampaignExecutor`/`CampaignScheduleChecker` temizliği (ayrı `chore`/`dead-code` işi).

## Riskler

- **Otomasyon auto-resume kırılması**: Guard yanlış kurulursa otomasyon-paused kampanya da başlatılamaz. Test: otomasyon paused (manuallyPaused=false) + mustBeStarted → START beklenir.
- **İşaret temizleme eksikliği**: unpause/start/stop'ta işaret temizlenmezse, manuel paused→unpause sonrası RUNNING kampanya scheduler tarafından yönetilemez hale gelebilir (örn. gün sonu auto-stop). Tüm clear noktaları te/st ile kilitlenir.
- **Race (scheduler okuma vs manuel pause yazma)**: Scheduler tick state'i okurken manuel pause action'ı ortada olabilir. İşaret set'i pause state geçişiyle aynı `action` (synchronized) içinde yapıldığından, scheduler ya pause-öncesi (RUNNING, start adımına girmez çünkü RUNNING startable değil) ya pause-sonrası (PAUSED+manuallyPaused) tutarlı durumu görür. Ara state PAUSING zaten startable değil.
- **Kalıcılık beklentisi**: İşaret in-memory ise JVM restart sonrası kaybolur; ancak restart sonrası context de yok → kampanya STOPPED olur, PAUSED kalmaz. Yani in-memory işaret bu fix için yeterli (bkz. Açık Sorular #1).
- **Hot path**: Değişiklik scheduler tick başına O(1) boolean okuma ve pause anında O(1) flag set; ek IO/lock/scan yok.

## TDD Stratejisi

`gbpd-tdd-workflow` + `gbpd-campaign-lifecycle-flow` uygulanır. Önce davranışı kilitleyen testler, sonra en küçük üretim kodu değişikliği.

`ScheduledCampaignRunnerTest` genişletmeleri (mevcut `putContext` yardımcı metodu `manuallyPaused` set edecek şekilde güncellenir):

- `doesNotStartWhenManuallyPaused_evenIfMustBeStartedTrue`: state PAUSED + manuallyPaused=true + mustBeStarted=true → `action(..., START, ...)` **never**.
- `startsWhenAutomationPaused_andMustBeStartedTrue`: state PAUSED + manuallyPaused=false + mustBeStarted=true → `action(id, START, null)` **times(1)** (auto-resume korunur).
- Mevcut `startsWhenStopped_andMustBeStartedTrue` / `doesNotStartWhenRunning_*` testleri korunur (regresyon yok).

Servis/facade düzeyi (işaret set/clear):

- `CampaignService.action(PAUSE, MANUAL)` → `dialerFacade.pauseCampaign(id, true)`; `action(PAUSE, null)` → `pauseCampaign(id, false)` (Mockito verify).
- `DialerFacade` testi: pauseCampaign(manual=true) RUNNING context'te `manuallyPaused=true`; unpause/start/shutdown sonrası `manuallyPaused=false`.

Test isimlendirmesi `<Konu><Davranış><TestTipi>Test`. PowerMock gerektirmez; Mockito + constructor/boundary yeterli. Sleep'e dayalı async test yazılmaz.

## Açık Sorular

Tüm açık sorular kapandı (2026-06-23).

- [x] **#1 İşaret kalıcılığı** — Cevap: **In-memory transient**. `manuallyPaused` yalnız in-memory `CampaignContext` campaign nesnesinde tutulur; DB/Hibernate migration yapılmaz. Paused state zaten JVM restart'ta korunmadığı için (context yok → STOPPED) kalıcılık ek fayda sağlamaz.
- [x] **#2 Auto-resume aksiyon semantiği** — Cevap: **Kapsam dışı**. Otomasyon-paused kampanyanın `CAMPAIGN_ACTION.START` (UNPAUSE değil) ile devam ettirilmesi bu fix'te değiştirilmez; mevcut davranış korunur. Gerekirse ayrı bir analiz/iş olarak ele alınır.
- [x] **#3 Gün-sonu STOP davranışı** — Cevap: **STOP devam etsin**. Manuel paused kampanya schedule penceresi bitince (`mustBeStopped`) mevcut davranışta olduğu gibi otomatik STOP edilir. Bu fix yalnız istenmeyen RESTART'ı engeller; gün-sonu STOP'a dokunmaz. Guard yalnız `shouldStartCampaign` içinde uygulanır, PreCheck short-circuit yapılmaz.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Açık Sorular (#1–#3) kullanıcı tarafından cevaplandı ve plan güncellendi (2026-06-23).
- [x] `ScheduledCampaignRunnerTest`'e manuel-paused (START yok) ve otomasyon-paused (START var) testleri eklendi.
- [x] İşaret set/clear için facade testleri eklendi (`DialerFacadeTest`: manuel pause → işaret set, otomasyon pause → set false, unpause/shutdown → temizle). Ayrı `CampaignServiceTest` repoda mevcut değil; PAUSE'taki `MANUAL.equals(stopType)` türetmesi tek satırlık olduğu için facade testleri + scheduler testleri kapsamı yeterli kabul edildi.
- [x] (A) `Campaign.manuallyPaused` (transient) işareti + `DialerFacade.pauseCampaign(Long, boolean)` set / `unpauseCampaign` + `shutdownCampaign` clear + `CampaignService.action` PAUSE case'i uygulandı.
- [x] (B) `ScheduledCampaignRunner.shouldStartCampaign` guard'ı uygulandı (`getCampaignState` → `getRuntimeCampaign` tek context fetch).
- [x] Hedefli testler yeşile çevrildi.
- [x] Refactor sonrası testler tekrar çalıştırıldı (tek koşu; 18 test yeşil).
- [x] `gbpd-call-flow-performance-guardrails` kontrol listesi gözden geçirildi: tick başına tek `boolean` okuma, pause anında tek `boolean` set; yeni IO/lock/scan/log yok.
- [x] `docs/architecture/CAMPAIGN-LIFECYCLE.md` "Durdurma ve Duraklatma" bölümüne manuel-pause sticky davranışı notu eklendi.
- [x] History kaydı `docs/history/23.06.2026.md` dosyasına append-only eklendi; yeni gün açıldığı için `docs/history/README.md` indeksi güncellendi.

## Performans Etkisi

- Hot path durumu: Scheduler tick dakikada bir; per-campaign değerlendirme hot path'in kenarındadır. Pause action operatör tetiklemeli, nadir.
- Call/event/tick başına ek maliyet: `shouldStartCampaign` içinde tek `boolean` okuma; pause anında tek `boolean` set. Ek IO/serialization/log yok.
- IO/scan/lock/serialization/log etkisi: Yeni geniş `synchronized` blok yok; `CampaignService.action` zaten synchronized. Yeni DB query yok (in-memory işaret tercih edilirse).
- Azaltım veya ölçüm notu: İşaret in-memory context campaign üzerinde tutulduğu için chain'in DB okuma yolu değişmez.

## Doğrulama

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunur; Maven offline çalıştırılır; eksik artifact varsa indirme denenmez.

Hedefli komutlar (uygulama aşamasında):

```text
mvn -o -Dtest=ScheduledCampaignRunnerTest test
```

İşaret set/clear için yeni servis/facade test sınıf adı netleşince ilgili `-Dtest=...` eklenir. Local tool path varsa komuta `-s <settings.xml>` ve `-Dmaven.repo.local=<repo>` eklenir. Frontend etkisi yoktur; Karma testleri kapsam dışıdır.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı veya `Yok` olarak işaretlendi.
- [x] Manuel paused kampanya schedule penceresinde scheduler tarafından START edilmez (`doesNotStartWhenManuallyPaused_evenIfMustBeStartedTrue`).
- [x] Otomasyon paused kampanya auto-resume davranışı korunur (`startsWhenAutomationPaused_andMustBeStartedTrue`).
- [x] İşaret unpause/shutdown'ta temizlenir; sonraki scheduler kararlarını engellemez (`DialerFacadeTest`).
- [x] Planlanan testler çalıştırıldı ve başarılı oldu.
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (2026-06-23). Üretim kodu değişiklikleri:

- `Campaign.java`: transient `manuallyPaused` alanı + `@Transient isManuallyPaused()` / `setManuallyPaused(boolean)` (DB'ye yazılmaz).
- `DialerFacade.java`: `pauseCampaign(Long, boolean manual)` imzası — RUNNING→PAUSING geçişinde `setManuallyPaused(manual)`; `unpauseCampaign` (PAUSED→STARTING) ve `shutdownCampaign` (→STOPPING) içinde `setManuallyPaused(false)`.
- `CampaignService.java`: `action` PAUSE case'i `dialerFacade.pauseCampaign(campaignId, Constants.CAMPAIGN_STOP_TYPE.MANUAL.equals(stopType))`.
- `ScheduledCampaignRunner.java`: `getCampaignState` → `getRuntimeCampaign` (tek context fetch); `processCampaignActions` state + `manuallyPaused`'i aynı snapshot'tan okur; `shouldStartCampaign(result, state, manuallyPaused)` PAUSED'ı yalnız `!manuallyPaused` iken startable kabul eder.

Test değişiklikleri:

- `ScheduledCampaignRunnerTest`: `doesNotStartWhenManuallyPaused_evenIfMustBeStartedTrue`, `startsWhenAutomationPaused_andMustBeStartedTrue` eklendi; 3 argümanlı `putContext(id, state, manuallyPaused)` helper'ı eklendi. (8 test)
- `DialerFacadeTest`: `shouldMarkManuallyPausedOnManualPause`, `shouldNotMarkManuallyPausedOnAutomationPause` eklendi; `shouldUnpausePausedCampaign` ve `shouldShutdownRunningCampaign`'a `setManuallyPaused(false)` doğrulaması eklendi; `shouldPauseRunningCampaign` yeni imzaya taşındı. (10 test)

Doğrulama:

```text
mvn -o -s C:/Users/GLB90114534/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90114534/.m2/repository -f gbpowerdialer/pom.xml -Dtest=ScheduledCampaignRunnerTest,DialerFacadeTest test
```

Sonuç: `BUILD SUCCESS`; `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`. Toplam süre ~31s. JDK 8 (1.8.0_432) + Maven 3.9.15 offline.
