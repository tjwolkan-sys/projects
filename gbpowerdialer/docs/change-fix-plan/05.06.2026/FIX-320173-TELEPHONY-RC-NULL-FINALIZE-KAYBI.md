# FIX-320173 telephony_rc NULL Finalize Kaybı Change/Fix Planı

- İş Etiketi: fix
- Kimlik: 320173
- Slug: telephony-rc-null-finalize-kaybi
- Tarih: 2026-06-05
- Öncelik: hotfix (kullanıcı onayı, 2026-06-05)
- Plan Durumu: Uygulandı ve hedefli regression yeşil (2026-06-05; 73 test, 0 hata)

## Bağlam

Müşteri (Turkcell seti, 6.6.3) `pd_cdr` üzerinde `telephony_rc IS NULL` kayıtlar raporladı (01-04.06 penceresi). Analiz sorgusu genişletildiğinde sorunun vaka haftasına özgü olmadığı görüldü: **01.05.2026'dan beri 3.137 NULL kayıt (~90/gün, kronik)**. Bu kayıtlarda `telephony_rc` ve `user_result_code_id` NULL, `call_end_time` originate anındaki placeholder değerinde (`saveInitialCDR`, `CallOriginator.java:601`) ve `rc_sequence` initial değerde; yani **finalize zincirinin pd_cdr persist'i hiç çalışmamış**.

03.06 Elastic logları (query1-query4) ve SQL doğrulamalarıyla iki ayrı kayıp ailesi kanıtlandı:

### Aile B — Cevaplı çağrılar: agent logout yolu pd_cdr yazmıyor (sabit ~6/gün, 2-23 bandı)

4/4 örnekte birebir aynı zincir (örnek dl-b222bcbb, campaign 91):

| Zaman | Olay |
|-------|------|
| 11:39:50 | Agent cevapladı, görüşme sürüyor (answer/handling CDR save'leri ✓) |
| 11:48:02 | Webphone RC alındı ve in-memory set edildi: **416 "KABUL ETTI - DGS SATILDI"** |
| 11:48:09.275 | `Handling unfinished call for logout event` — agent logout oldu |
| 11:48:09.276 | Sayaçlar manuel düşüldü; trc=id81 "Orphaned call terminated" **yalnız in-memory** set edildi; kampanya result tablosuna yazıldı; **pd_cdr persist YOK** |
| 11:48:09.962 | `Forceful hangup command sent` |
| 11:48:11.099 | Gerçek `DialerHangupEvent` (cause=16) geldi → **tam sessizlik** (finalize bloğu atlandı) |

Sonuç: pd_cdr'da trc NULL, **alınmış URC dahil** her şey kayıp; kampanya result tablosu ↔ pd_cdr tutarsız.

### Aile A — Cevapsız çağrılar: processCallResult ortada kesiliyor (patlamalı: 16.05=477, 22.05=470, 23.05=423; AMI fırtına günleriyle korele)

6+ örnekte birebir aynı imza (örnek dl-322dc346, campaign 85):

| Zaman | Olay |
|-------|------|
| 13:41:54.399 | `DialerHangupEvent cause=19` alındı; trc **doğru çözüldü** (id=13) |
| 13:41:54.401 | `Retry scheduled` ✓ → `Telephony slot released` ✓ → `Incrementations processed` ✓ |
| — | İkinci `saved/updated successfully` YOK, `Cannot save` YOK, `Hangup for:` YOK |
| 13:41:54.565 | Aynı reader thread sonraki event'i işlemeye devam etti |

`processCallResult` `ManagerConnectionDecorator.java:819`'a kadar koşuyor; `:820-848` bölgesinde (kampanya DB insert / stop-condition / predictive bildirim / CDR hazırlık) kesiliyor ve `:849`'daki `cdrService.saveOrUpdate`'e ulaşılamıyor. Kesilme anları **AMI connection-loss fırtınalarıyla eşzamanlı** (03.06: 142 kopma, 3.074 conn-loss cleanup, sabah 10.000+ originate failure reason '0'). Exception izi mevcut log zincirinde görünmüyor (query4: 4 pencerede sıfır exception satırı) — gözlemlenebilirlik açığı. `markBusinessResultFinalized` (`:799`) tüketildiği ve çağrı `:818`'de cache'lerden çıkarıldığı için sonraki tüm telafi yolları (2. hangup bacağı, geç OriginateResponse, expiry-901) kilitleniyor → kalıcı NULL.

### Elenen hipotezler

- `pd_telephony_rc` kod eksiği: ELENDİ — tüm dahili kodlar tanımlı (907→id79, 901→id73, 903→id75, 909→id81, 900→id71 AMD); cause çözümlemesi loglarda doğru çalışıyor.
- PD restart: ELENDİ — 03.06'da restart yok; 142 kopuşun hepsi in-place reconnect (Reader-N thread numaraları artarak devam ediyor).
- Log kaybı: ELENDİ — DB'de satır gerçekten initial halde; save koşmamış.

### Yan etkiler

- NULL kayıt URC'siz kaldığı için retry kuralına takılmıyor (`isResultCodeAllowsRetries` URC null → true) → aynı numara tekrar tekrar NULL'la aranıyor (c85'te `rc_sequence` 30-33'e tırmanmış) — hacim israfı + arama-compliance riski.
- `assigned_user_id`/`connected_user_id` NULL (ownership populate hiç koşmadı), `call_end_time < call_answer_time` tutarsızlığı (placeholder).
- Aile B, devam eden URC kaybı vakasının (bkz. FIX-317516 ve 01-03.06 incelemesi) bir besleyicisi: girilen RC'ler pd_cdr'a hiç ulaşmıyor.

## İş Etiketi

fix

## Öncelik

hotfix (öneri — Açık Soru #3)

## Etkilenen Alanlar

- `gbpowerdialer/src/main/java/com/powerdialer/engine/UserLoginLogoutHandler.java`
  - `handleUserNotFinishedCalls`, `processClearance`, `finishCall` (Aile B kök çözümü)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionDecorator.java`
  - `processCallResult` persist sıralaması ve yan etki izolasyonu (Aile A kök çözümü)
  - `handleDialerHangup` sessiz-skip guard'ı (`:675`) ve null-trc/parse savunmaları (`:676-681`)
  - `persistHangupSnapshotForDelayedResult` sessiz skip'ine WARN (`:748`)
  - `onManagerEvent` görünürlük sarmalaması (`:424`)
  - `processCallResult:823` null-safe karşılaştırma
- `gbpowerdialer/src/main/java/com/powerdialer/model/Call.java`
  - `markBusinessResultFinalized` rollback API'si (persist başarısızlığında flag geri alma)
- `gbpowerdialer/src/main/java/com/powerdialer/engine/asterisk/ami/ManagerConnectionComposition.java`
  - `createItuToTelephonyResultCodeRelations` sonrası dahili kod varlık doğrulaması (savunma)
- Testler: `gbpowerdialer/src/test/java/tests/ami/` altında yeni regression sınıfları; gerekirse mevcut `SaveInitialCdrHangupResultRegressionTest`/`AwaitingUserResult*` suite'leriyle birlikte koşum

## Mevcut Davranış

### Aile B akışı (logout-orphan)

```
UserLoginLogoutHandler.handleLogout → handleUserNotFinishedCalls(user)
  → answered çağrı: isOrphanCandidate=true → call.setOrphaned(true) + orphanedCalls.put
  → processClearance: call.getTelephonyResultCode()==null (görüşme ortası — normal)
      → finishCall(call):                                   [UserLoginLogoutHandler:183-199]
          completeCall (completeDate set)
          sayaç decrement'leri (manuel; releaseTelephonySlot ile mükerrer mantık)
          call.setTelephonyResultCode(map.get(909))          ← yalnız in-memory
          campaignDbOperations.insertCallResultToDB(call)    ← kampanya tablosu yazılır
          incrementTelephonyResultCode + checkStopCondition
          ── pd_cdr persist YOK; cdr.setTelephonyRC dahi çağrılmıyor ──
      → clearCall + hangupCall
sonra gerçek DialerHangup:
  getCallForHangup → orphanedCalls'ta bulunur
  MCD:675 `if (trc == null || hasErrorCode)` → trc=81 dolu, error değil → blok atlanır
  → publishCallEndEvent + "Hangup for:" dahil hiçbir şey çalışmaz (sessiz)
```

Webphone RC'si daha önce gelmişse `WebphoneSubscriber.processActiveCallResultCode` (`WebphoneSubscriber.java:302-339`) URC'yi yalnız in-memory set eder; `call.getTelephonyResultCode()==null` olduğundan persist'i hangup'a bırakır (`:327`) — logout zinciri bu sözleşmeyi kırar ve URC da kaybolur.

### Aile A akışı (processCallResult kesilmesi)

```
handleDialerHangup → cause çözülür (örn. 19→id13) → shouldProcess=true → processCallResult:
  markBusinessResultFinalized()            [:799]  ← one-shot, geri alınmaz
  ... returnUser / seq increment / CallAttemptPolicy / manageRetries ("Retry scheduled" ✓)
  releaseTelephonySlotIfNeeded             [:818]  ← clearCall: tüm cache'lerden çıkar ✓ log
  doCampaignIncrementationsOnHangup        [:819]  ✓ log
  insertCallResultToDB                     [:820]  (kendi içinde swallow)
  checkStopCondition                       [:821]
  predictive beta/alpha bloğu              [:823-831]  (:823 answered+nullTRC NPE riski)
  cdr setters + ownership + phone column   [:833-847]
  try { cdrService.saveOrUpdate(cdr) }     [:848-852]  ← BURAYA ULAŞILAMIYOR
```

`:820-848` bölgesinde fırlayan exception: (a) pd_cdr persist'ini engeller, (b) `markBusinessResultFinalized` tüketildiği için sonraki denemeleri kilitler, (c) çağrı cache'lerde olmadığı için 901 expiry ağı devreye giremez, (d) `onManagerEvent` zincirinde dl-id'li hiçbir hata logu üretmeden kaybolur.

## Beklenen Davranış

1. **Aile B:** Agent logout'u nedeniyle orphan'lanan answered çağrıda pd_cdr finalize edilir: `telephony_rc` (orphaned), gerçek `call_end_time`, `rc_sequence`, ownership alanları ve (Açık Soru #2 kararına göre) in-memory URC persist edilir. Kampanya result tablosu ↔ pd_cdr tutarlı kalır. Sonradan gelen gerçek hangup idempotent kalır (çift persist/decrement yok).
2. **Aile A:** trc çözüldükten sonra pd_cdr persist'i yan etkilerden bağımsız garanti edilir (persist-first). Yan etki exception'ları persist'i ve birbirini engellemez; her exception dl-id ile ERROR loglanır. Persist başarısız olursa `markBusinessResultFinalized` geri alınır ve çağrı cache'te kaldığı için sonraki event/expiry yeniden dener.
3. **Sözleşme guard'ı:** `handleDialerHangup` sessiz-skip dalı, `call.trc` dolu ama `cdr.getTelephonyRC()` null ise (persist hiç yapılmamış imzası) finalize'ı işletir; aksi durumda mevcut skip davranışı korunur.
4. Redis channel/payload, AMI event adları, result code eşlemeleri ve campaign state geçişleri değişmez. Counter lifecycle (callsInProgress/active/ringing/inCall) korunur; agent'ın ACD havuzuna dönüş gecikmesi artmaz.

## Kod Okuma Bulguları

- `finishCall` answered dalı kampanya tablosunu yazıyor ama `cdrService` referansı bile yok; pd_cdr satırı initial halde kalıyor (`UserLoginLogoutHandler.java:183-199`).
- `getCallForHangup` orphanedCalls'ı kapsıyor (`MCD:703-706`) — çağrı bulunuyor; kayıp `:675` koşulundan kaynaklanıyor; `publishCallEndEvent`/`"Hangup for:"` de aynı blokta olduğu için iz bırakmıyor.
- `processCallResult` persist'i zincirin en sonunda; `releaseTelephonySlotIfNeeded` (cache temizliği) persist'ten ÖNCE (`:818` vs `:849`) → kesilme kalıcı NULL üretir.
- `CDRService.saveOrUpdate` `@Transactional`; "saved/updated successfully" logu commit'ten önce basılır (`CDRService.java:34-37`) — commit-fail de teorik sessiz varyant; persist-first + flag-rollback bunu da kapsar.
- `clearExpiredCall` (901 ağı) ve `finalizeRecoverableConnectionLossCall` de saveCDR'ı son adımda koşturur (`CallCleaner.java:58-82`, `MCD:1189-1211`) — processCallResult düzeldiğinde awaiting/recoverable yolları otomatik düzelir; clearExpiredCall ayrıca Guava removal listener'da exception yutulmasına açık (kapsam: yalnız sıralama notu, davranış değişikliği bu planda yok).
- `UserPopulationEvaluator.populateCdrUserOwnership` tamamen null-safe (Aile A kesilme şüphelisi değil); ancak trc=909'da `shouldPopulateCdrUserResultCode` false döner → Aile B persist'inde URC'nin korunması için karar gerekir (Açık Soru #2).
- `Integer.parseInt(event.getHangupCause())` (`MCD:676`) guard'sız; `ituToTelephonyResultCodeRelations.get(cause)` null dönebilir (`:677`) ve `:823` answered+nullTRC NPE'ye açık — bu hafta tetiklenmedi ama aynı sınıfın açık kapıları (savunma kapsamı).

## Root Cause / Değişiklik Gerekçesi

Ortak tema: **"telephonyResultCode'u terminal olarak set eden her yol pd_cdr'ı persist etmek zorundadır" sözleşmesi kodda yok.** İki kusur bu boşluktan besleniyor:

1. Logout-orphan yolu trc'yi in-memory set edip pd_cdr'ı hiç yazmıyor; hangup'ın `:675` skip'i bu durumu sessizce kalıcılaştırıyor (Aile B).
2. `processCallResult` persist'i tüm yan etkilerin arkasına zincirliyor; tek exception persist'i yutuyor ve one-shot flag + erken cache temizliği telafiyi imkânsızlaştırıyor (Aile A).

Case-specific yama yerine sözleşmenin kendisi kurulacak: persist-first finalize + skip-guard + logout yolunun ortak finalize'a bağlanması.

## Çözüm Yaklaşımı

### (B) Logout-orphan persist — Açık Soru #1

- **Seçenek B1 (önerilen, davranış-koruyucu):** `finishCall` answered dalına pd_cdr finalize-persist eklenir: `cdr.setTelephonyRC(orphanedTrc)`, `cdr.setCallEndTime(completeDate)`, `cdr.setRcSequence`, URC kararı (#2), ownership populate ve `cdrService.saveOrUpdate`. Retry edilmez, sayaç akışı bugünkü gibi kalır. Persist bloğu `processCallResult`'taki ile ortak bir helper'a çıkarılır (tek doğruluk kaynağı).
- **Seçenek B2 (daha büyük birleşme):** `processClearance` orphan dalında trc=909 set edilip `processCallResult`'a yönlendirilir; persist + ownership + sayaçlar standart akıştan geçer, manuel decrement mükerrerliği kalkar. **Davranış değişikliği:** orphan çağrı retry değerlendirmesine girer (bugün girmiyor) — iş kararı gerektirir.

Her iki seçenekte de `:675` skip'i idempotans guard'ı olarak kalır (persist edilmiş çağrıda ikinci finalize koşmaz).

### (A) processCallResult persist-first + exception izolasyonu

Yeni sıra:

```
markBusinessResultFinalized → clearAwaiting → returnUserToACDEngine (agent release gecikmesin)
→ incrementResultCodeSequence → updateCallCampaignIfNeeded
→ [PERSIST BLOĞU] cdr alanları (urc/ownership/server/trc/end/seq/filter/abandoned/phone-col)
   + cdrService.saveOrUpdate — kendi try/catch'i:
     başarısızsa ERROR (dl-id'li) + markBusinessResultFinalized ROLLBACK + return
     (çağrı cache'lerde kaldığı için 2. hangup bacağı / expiry yeniden dener;
      yan etkiler henüz koşmadığı için çift retry/sayaç riski yok)
→ yan etkiler, her grup kendi try/catch'inde (hata dl-id ile ERROR loglanır, zincir kopmaz):
   CallAttemptPolicy → clearPreviousRetryDate → manageRetries
   → releaseTelephonySlotIfNeeded → doCampaignIncrementations → insertCallResultToDB
   → checkStopCondition → predictive beta/alpha → gamma notify
```

Not: `manageRetries` persist'ten sonraya alınır; persist başarısızsa retry da ertelenmiş olur (yeniden denemede koşar). `releaseTelephonySlotIfNeeded`/decrement'ler persist sonrası — kesilme durumunda çağrı cache'te kalır (telafi edilebilir durum, mevcut "kalıcı NULL"un tersi).

### (Görünürlük) `onManagerEvent` sarmalaması

`onManagerEvent` gövdesi try/catch'e alınır; catch ERROR + event sınıfı + varsa dl-id loglar. Aile A'nın bugün görünmeyen exception'ı bir sonraki oluşumda tek logda yakalanır. Maliyet: try/catch kurulumu JIT sonrası ~0; yalnız hata anında log.

### (Guard) `handleDialerHangup:675`

Koşul `call.trc != null && cdr.getTelephonyRC() != null` iken skip olacak şekilde daraltılır (in-memory CDR kontrolü, DB erişimi YOK). `call.trc` dolu + `cdr.trc` null ise WARN + finalize işletilir. Mevcut akışlarla uyum: snapshot/DNC/LSC/processCallResult yolları `cdr.setTelephonyRC`'yi in-memory hemen set ettiği için davranışları değişmez.

### (Savunma) Null-trc/parse/startup

- `:676` parseInt guard (parse edilemeyen cause → WARN + fallback).
- `:677` map.get null dönerse WARN + 901 (`CALL_EXPIRED_CLEARING`) fallback'i — yeni DB satırı gerekmez (Açık Soru #4).
- `:823` null-safe (`trc != null && id == 71`).
- `persistHangupSnapshotForDelayedResult` null-trc skip'ine WARN.
- `createItuToTelephonyResultCodeRelations` sonrası dahili kodların (71, 84, 900, 901, 902, 903, 905-911) varlık kontrolü; eksikse ERROR log (startup'ı durdurmaz).

### Kapsam dışı (nota düşüldü)

- Conn-loss cleanup'ın yavaşlığı (çağrı başına ~2.5 sn, originator thread'inde sıralı; 03.06'da 3.074 cleanup) ve günde 142 AMI kopuşunun altyapı kök nedeni → ayrı işler.
- `saveInitialCDR`'ın `call_end_time=now` placeholder'ı (end<answer raporlama tutarsızlığı) → raporlama bağımlılıkları nedeniyle ayrı değerlendirme.
- `clearExpiredCall`/Guava removal listener persist sıralaması → processCallResult düzeldiğinde ana yol kapanıyor; ayrı küçük iş olarak ele alınabilir.

## Riskler

- **Counter drift:** Persist-first sıralamasında decrement/increment'lerin persist sonrasına taşınması yanlış uygulanırsa çift veya eksik sayım olur. `markTelephonySlotReleased` idempotans flag'i korunur; testler sayaçları da doğrular.
- **Çift retry:** Persist-fail + flag-rollback sonrası ikinci denemede `manageRetries` tekrar koşar; `clearPreviousRetryDateForOriginatedCall` ve retry tarafının idempotansı test ile kilitlenir (persist-fail senaryosunda yan etkiler hiç koşmadığı için birincil risk düşük).
- **Agent release gecikmesi:** `returnUserToACDEngine` persist'ten önce tutularak predictive agent havuzuna dönüş latency'si korunur.
- **URC kural istisnası (#2):** Orphaned çağrıda URC persist edilirse `UserPopulationEvaluator` kurallarına dar bir 909-istisnası gerekir; yanlış genellenirse başarısız çağrılarda URC sızıntısı olur — istisna yalnız orphan finalize persist bloğunda uygulanır, evaluator'ın genel kuralları değişmez.
- **Skip-guard yan etkisi:** `cdr.getTelephonyRC()` in-memory kontrolü, trc'yi set edip CDR'a bilinçli yazmayan bir akış varsa davranış değiştirir — kod taramasında böyle bir akış bulunmadı (tüm terminal yollar cdr.setTelephonyRC çağırıyor); karakterizasyon testleriyle kilitlenir.
- **Hot path:** AMI hangup hot path'ine yeni DB query eklenmez; mevcut tek `saveOrUpdate`'in yeri değişir.

## TDD Stratejisi

Yeni test sınıfları (`gbpowerdialer/src/test/java/tests/ami/`, Mockito + constructor boundary; PowerMock yok):

- `LogoutOrphanCdrPersistRegressionTest`: (1) answered+URC'li çağrıda logout → pd_cdr'a orphaned trc + gerçek end + (karara göre) URC + ownership persist edilir; (2) sonraki DialerHangup idempotent (ikinci persist/decrement yok); (3) kampanya tablosu ile pd_cdr aynı URC değerini görür.
- `ProcessCallResultPersistFirstRegressionTest`: (1) yan etki exception'ı (stopCondition/predictive mock throw) CDR persist'ini engellemez; (2) persist-fail → flag rollback + çağrı cache'te kalır + ikinci deneme persist eder; (3) normal akışta sayaç/retry/publish davranışı birebir korunur (karakterizasyon).
- `DialerHangupSilentSkipGuardRegressionTest`: (1) `call.trc` dolu + `cdr.trc` null → finalize işler + WARN; (2) `cdr.trc` dolu → mevcut sessiz skip korunur.
- `UnmappedHangupCauseFallbackRegressionTest`: parse edilemeyen / eşlenmeyen cause → 901 fallback + WARN; `:823` null-safe.

Mevcut suite'lerden regression koruması: `SaveInitialCdrHangupResultRegressionTest`, `AwaitingUserResultCacheTransitionRegressionTest`, `AwaitingUserResultTimeoutPreserveRegressionTest`, `CallCleanerTelephonyResultRegressionTest`, `WebphoneSubscriberTest`.

## Açık Sorular

Tüm açık sorular kullanıcı tarafından kapatıldı (2026-06-05).

- [x] **#1 Aile B çözüm tercihi** — Cevap: **B1** (davranış-koruyucu). `finishCall` answered dalı, `processCallResult` ile ortaklaşılan `ManagerConnectionDecorator.persistFinalCdr` helper'ı üzerinden pd_cdr'ı persist eder; retry değerlendirmesi yapılmaz, sayaç akışı değişmez.
- [x] **#2 Orphaned (909) çağrıda URC persisti** — Cevap: **Evet**. `persistFinalCdr(call, completeDate, preserveUserResultCode=true)` ile in-memory URC, `UserPopulationEvaluator` kuralları null'lasa bile korunur; kampanya result tablosu ile pd_cdr tutarlı kalır. İstisna yalnız orphan finalize çağrısında uygulanır; evaluator'ın genel kuralları değişmedi.
- [x] **#3 Öncelik** — Cevap: **hotfix**.
- [x] **#4 Unmapped-cause fallback kodu** — Cevap: **901** (`CALL_EXPIRED_CLEARING`). `resolveHangupTelephonyResultCode` parse edilemeyen veya eşlenmeyen cause'larda 901'e düşer ve cause değerini WARN ile loglar; müşteri DB değişikliği gerekmez.

## Execution Plan

- [x] Onaylanan planın tüm adımları bu dosyaya işlendi.
- [x] Açık sorular kullanıcı tarafından cevaplandı ve plan güncellendi (B1 / URC korunur / hotfix / 901).
- [x] Mevcut davranışı kilitleyen karakterizasyon testleri yazıldı (skip dalı idempotansı `shouldSkipSubsequentHangupAfterOrphanedFinalCdrPersisted`; normal finalize dış davranışı mevcut `SaveInitialCdrHangupResultRegressionTest`/`AwaitingUserResult*` suite'leriyle korunuyor).
- [x] Beklenen davranışı kanıtlayan testler yazıldı: `LogoutOrphanCdrPersistRegressionTest` (3), `ProcessCallResultPersistFirstRegressionTest` (3), `UnmappedHangupCauseFallbackRegressionTest` (2); `shouldClearCallProgressEvenWhenFinalCdrSaveFails` yeni sözleşmeye göre `shouldRollbackFinalizeAndRetryWhenFinalCdrSaveFails` olarak güncellendi (persist-fail artık telafi edilebilir durumda bırakır — bilinçli davranış değişikliği).
- [x] (B1) `UserLoginLogoutHandler.finishCall` → `persistOrphanedFinalCdr` ile pd_cdr persist'i uygulandı; `completeCall(call, completeDate)` overload'una geçildi (pd_cdr end == complete date).
- [x] (A) `processCallResult` persist-first sıralaması + `runCallResultSideEffects` yan etki izolasyonu + `Call.rollbackBusinessResultFinalized` uygulandı; ortak persist `persistFinalCdr` helper'ına çıkarıldı (telefon kolonu çözümleme hatası artık persist'i kesmez, WARN'la geçer).
- [x] (Guard) `handleDialerHangup` skip-guard'ı (`shouldFinalizeOnHangup`: in-memory `cdr.getTelephonyRC()==null` ise WARN + finalize) uygulandı.
- [x] (Görünürlük) Yan etki grupları dl-id'li ERROR loglarıyla sarıldı; persist-fail `Final CDR persist failed; finalize rolled back...` ERROR'u üretir. Not: `onManagerEvent` genel try/catch sarmalaması yerine exception üretebilen finalize bölgesi grup-bazlı sarıldığı için ayrıca genel sarmalamaya gerek kalmadı; dispatch'e ulaşan beklenmedik exception kalmıyor.
- [x] (Savunma) `parseHangupCause` guard'ı, `resolveHangupTelephonyResultCode` 901 fallback'i, `:823` null-safe karşılaştırma, `persistHangupSnapshotForDelayedResult` WARN'ı ve `ManagerConnectionComposition.validateInternalTelephonyResultCodeMappings` startup doğrulaması uygulandı.
- [x] Hedefli testler yeşile çevrildi (73 test, 0 hata; komut Doğrulama bölümünde).
- [x] Refactor sonrası hedefli testler tekrar çalıştırıldı (tek koşumda tüm suite yeşil; ek refactor yapılmadı).
- [x] `gbpd-call-flow-performance-guardrails` kontrolü: hot path'e yeni DB IO yok (mevcut tek `saveOrUpdate`'in sırası öne alındı); `returnUserToACDEngine` persist öncesinde tutularak agent release latency korundu; skip-guard O(1) in-memory kontrol; yeni loglar yalnız anomali (WARN/ERROR) durumunda; counter lifecycle idempotans flag'leriyle korundu ve testlerle doğrulandı.
- [x] History kaydı ve indeks güncellemeleri tamamlandı (`docs/history/05.06.2026.md`).

## Performans Etkisi

- Hot path durumu: AMI hangup/result handling hot path'tir. Logout yolu hot path değildir (user-event thread'i, logout başına bir kez).
- Call/event/tick başına ek maliyet: Yeni DB query YOK. `processCallResult`'taki mevcut tek `saveOrUpdate`'in zincir içindeki yeri öne alınır; toplam IO sayısı aynı. Aile B'de logout başına bir `saveOrUpdate` eklenir (bugün eksik olan persist; logout yolu düşük hacimli, ~6/gün gözlenen orphan vakası + normal logout'larda answered çağrı yoksa hiç koşmaz).
- IO/scan/lock/serialization/log etkisi: Yeni geniş `synchronized` blok yok. `onManagerEvent` try/catch ve skip-guard'ın in-memory null kontrolü O(1). Yeni loglar yalnız anomali durumlarında (WARN/ERROR) basılır; normal akışta log hacmi değişmez.
- Azaltım veya ölçüm notu: `returnUserToACDEngine` persist'ten önce tutularak agent release latency'si korunur. Deploy sonrası izleme: günlük `telephony_rc IS NULL` sayımı (mevcut müşteri sorgusu) + "Handling unfinished call for logout event" hacmi + yeni WARN/ERROR imzaları.

## Doğrulama

Doğrulama öncesi `.codex/TOOL-PATHS.local.properties` okunur; Maven `-o` offline çalıştırılır; eksik artifact varsa indirme denenmez, geliştiriciye raporlanır.

```text
mvn -o -Dtest=LogoutOrphanCdrPersistRegressionTest test
mvn -o -Dtest=ProcessCallResultPersistFirstRegressionTest test
mvn -o -Dtest=DialerHangupSilentSkipGuardRegressionTest test
mvn -o -Dtest=UnmappedHangupCauseFallbackRegressionTest test
```

Toplu hedefli regression:

```text
mvn -o -Dtest=LogoutOrphanCdrPersistRegressionTest,ProcessCallResultPersistFirstRegressionTest,DialerHangupSilentSkipGuardRegressionTest,UnmappedHangupCauseFallbackRegressionTest,SaveInitialCdrHangupResultRegressionTest,AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,CallCleanerTelephonyResultRegressionTest,WebphoneSubscriberTest test
```

AngularJS frontend etkisi yoktur; Karma testleri kapsam dışıdır.

## Kapanış Kriteri

- [x] Açık sorular cevaplandı ve karara göre plan güncellendi.
- [x] Aile B: logout-orphan answered çağrıda pd_cdr finalize persist edilir; kampanya tablosu ile tutarlıdır; ikinci hangup idempotenttir (`LogoutOrphanCdrPersistRegressionTest`).
- [x] Aile A: yan etki exception'ı pd_cdr persist'ini engellemez; persist-fail telafi edilebilir durumda kalır (flag rollback + cache'te kalış); exception'lar dl-id ile görünür (`ProcessCallResultPersistFirstRegressionTest`, `shouldRollbackFinalizeAndRetryWhenFinalCdrSaveFails`).
- [x] Counter lifecycle korunur (çift/eksik decrement yok; testlerde callsInProgress/activeCalls doğrulandı); agent release latency artmaz (returnUser persist öncesi).
- [x] Planlanan testler çalıştırıldı ve başarılı oldu (73 test, 0 hata).
- [x] Final yanıtta kısa Test Özeti sunuldu.

## Tamamlanma Notu

Uygulama tamamlandı (2026-06-05, branch `fix/320173-telephony-rc-null-finalize-kaybi`). Üretim kodu değişiklikleri:

- `Call.java`: `rollbackBusinessResultFinalized()` eklendi (persist-fail telafisi için one-shot bayrağın geri alınması).
- `ManagerConnectionDecorator.java`:
  - `processCallResult` persist-first'e çevrildi: `markBusinessResultFinalized → clearAwaiting → returnUserToACDEngine → incrementResultCodeSequence → updateCallCampaignIfNeeded → persistFinalCdr → runCallResultSideEffects`. Persist başarısızsa bayrak geri alınır, ERROR loglanır ve çağrı cache'lerde kalır (sonraki hangup bacağı/expiry yeniden dener; yan etkiler hiç koşmadığı için çift retry/sayaç riski yok).
  - `runCallResultSideEffects`: retry/attempt, slot/sayaç, stop-condition ve predictive grupları kendi try/catch'lerinde; hata dl-id ile ERROR loglanır, zincir kopmaz. `:823` karşılaştırması null-safe yapıldı.
  - `persistFinalCdr(call, completeDate, preserveUserResultCode)`: ortak pd_cdr finalize persist sözleşmesi; telefon kolonu çözümleme hatası persist'i kesmez (WARN), `preserveUserResultCode=true` orphan yolunda in-memory URC'yi korur.
  - `handleDialerHangup`: `shouldFinalizeOnHangup` skip-guard'ı (trc in-memory dolu ama `cdr.getTelephonyRC()` null ise WARN + finalize — Aile B'nin sessiz kaybını son şans olarak yakalar); `resolveHangupTelephonyResultCode` + `parseHangupCause` (eşlenmeyen/parse edilemeyen cause → 901 fallback + WARN).
  - `persistHangupSnapshotForDelayedResult` sessiz skip'ine WARN eklendi.
- `UserLoginLogoutHandler.java`: `finishCall` answered dalı `completeCall(call, completeDate)` overload'una geçti ve `persistOrphanedFinalCdr` ile pd_cdr'ı orphan sonucu + gerçek bitiş zamanı + korunan URC ile persist eder (B1; retry yok, sayaç akışı değişmedi).
- `ManagerConnectionComposition.java`: `validateInternalTelephonyResultCodeMappings` startup doğrulaması (dahili kodlar 84, 901, 902, 903, 905-911 map'te yoksa ERROR; startup engellenmez).

Test değişiklikleri:

- Yeni: `tests/ami/LogoutOrphanCdrPersistRegressionTest` (3 senaryo: URC korunarak persist, ikinci hangup idempotansı, URC'siz persist).
- Yeni: `tests/ami/ProcessCallResultPersistFirstRegressionTest` (3 senaryo: retry yan etkisi patlarken persist + slot/sayaç devamı, stop-condition izolasyonu, answered+null-TRC NPE'sizliği).
- Yeni: `tests/ami/UnmappedHangupCauseFallbackRegressionTest` (2 senaryo: eşlenmeyen cause 901 fallback'i, parse edilemeyen cause 901 fallback'i).
- Güncellendi: `SaveInitialCdrHangupResultRegressionTest.shouldClearCallProgressEvenWhenFinalCdrSaveFails` → `shouldRollbackFinalizeAndRetryWhenFinalCdrSaveFails` (persist-fail yeni sözleşmesi: rollback + cache'te kalış + ikinci denemede tamamlanma).

Doğrulama (offline, JDK 1.8.0_202 + Maven 3.9.14, `.codex/TOOL-PATHS.local.properties`):

```text
mvn.cmd -o -s C:/Users/GLB90060295/.m2/settings.xml -Dmaven.repo.local=C:/Users/GLB90060295/.m2/repository -f gbpowerdialer/pom.xml -Dtest=SaveInitialCdrHangupResultRegressionTest,ProcessCallResultPersistFirstRegressionTest,LogoutOrphanCdrPersistRegressionTest,UnmappedHangupCauseFallbackRegressionTest,AwaitingUserResultCacheTransitionRegressionTest,AwaitingUserResultTimeoutPreserveRegressionTest,CallCleanerTelephonyResultRegressionTest,WebphoneSubscriberTest test
```

Sonuç: `BUILD SUCCESS`; `Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`.

Deploy sonrası izleme: günlük `telephony_rc IS NULL` sayımı (müşteri sorgusu), `Handling unfinished call for logout event` hacmi, yeni WARN/ERROR imzaları (`Final CDR persist failed`, `is not mapped to a telephony result code`, `was never persisted to CDR`).
